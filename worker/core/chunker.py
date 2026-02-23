import re
import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)

@dataclass
class Chunk:
    index: int
    content: str
    token_count: int
    metadata: dict

def chunk_text(
    text: str,
    chunk_size: int = 512,
    chunk_overlap: int = 50,
    document_title: str = ""
) -> list[Chunk]:
    """
    Chunk text using a semantic-aware sliding window strategy.

    Strategy:
    1. First, split by major section boundaries (double newlines, headers)
    2. Within sections, use token-based sliding window with overlap
    3. Never break mid-sentence if possible
    """
    if not text or not text.strip():
        return []

    # Step 1: Split into semantic sections
    sections = split_into_sections(text)

    # Step 2: Chunk each section with sliding window
    chunks = []
    global_index = 0

    for section in sections:
        section_chunks = sliding_window_chunk(
            section['content'],
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap
        )

        for chunk_text_content in section_chunks:
            token_count = estimate_tokens(chunk_text_content)

            chunks.append(Chunk(
                index=global_index,
                content=chunk_text_content,
                token_count=token_count,
                metadata={
                    "section_title": section.get('title', ''),
                    "heading_hierarchy": section.get('hierarchy', []),
                    "document_title": document_title,
                }
            ))
            global_index += 1

    logger.info(
        f"Chunked into {len(chunks)} chunks "
        f"(avg {sum(c.token_count for c in chunks) // max(len(chunks), 1)} tokens/chunk)"
    )
    return chunks

def split_into_sections(text: str) -> list[dict]:
    """
    Split text into semantic sections based on headings and paragraph breaks.
    Preserves heading hierarchy for metadata.
    """
    heading_pattern = re.compile(r'^(#{1,6})\s+(.+)$', re.MULTILINE)

    sections = []
    current_hierarchy = []
    last_pos = 0

    for match in heading_pattern.finditer(text):
        content_before = text[last_pos:match.start()].strip()
        if content_before:
            sections.append({
                'title': current_hierarchy[-1] if current_hierarchy else '',
                'hierarchy': list(current_hierarchy),
                'content': content_before
            })

        level = len(match.group(1))
        heading_text = match.group(2).strip()

        current_hierarchy = current_hierarchy[:level-1]
        current_hierarchy.append(heading_text)

        last_pos = match.end()

    remaining = text[last_pos:].strip()
    if remaining:
        sections.append({
            'title': current_hierarchy[-1] if current_hierarchy else '',
            'hierarchy': list(current_hierarchy),
            'content': remaining
        })

    if not sections:
        sections = [{'title': '', 'hierarchy': [], 'content': text.strip()}]

    return sections

def sliding_window_chunk(
    text: str,
    chunk_size: int = 512,
    chunk_overlap: int = 50
) -> list[str]:
    """
    Split text into overlapping chunks using word boundaries.
    Tries to break at sentence boundaries when possible.
    """
    words = text.split()

    if len(words) <= chunk_size:
        return [text.strip()] if text.strip() else []

    chunks = []
    start = 0
    step = chunk_size - chunk_overlap

    while start < len(words):
        end = min(start + chunk_size, len(words))
        chunk_words = words[start:end]
        chunk_text = ' '.join(chunk_words)

        # Try to break at sentence boundary
        if end < len(words):
            lookback = max(1, len(chunk_words) // 5)
            for i in range(len(chunk_words) - 1, len(chunk_words) - lookback, -1):
                if chunk_words[i].endswith(('.', '!', '?', '."', '?"')):
                    chunk_text = ' '.join(chunk_words[:i+1])
                    break

        chunks.append(chunk_text.strip())
        start += step

    return [c for c in chunks if c]

def estimate_tokens(text: str) -> int:
    """Rough token estimation: ~0.75 words per token for English."""
    word_count = len(text.split())
    return int(word_count / 0.75)
