import logging
from pathlib import Path

logger = logging.getLogger(__name__)

def extract_text(file_path: str, content_type: str) -> tuple[str, dict]:
    """
    Extract text from a document.
    Returns (text, metadata) tuple.
    """
    extractors = {
        'pdf': extract_pdf,
        'markdown': extract_markdown,
        'text': extract_text_file,
    }

    extractor = extractors.get(content_type)
    if not extractor:
        raise ValueError(f"Unsupported content type: {content_type}")

    return extractor(file_path)

def extract_pdf(file_path: str) -> tuple[str, dict]:
    """Extract text from PDF using PyPDF2 with pdfplumber fallback."""
    import PyPDF2

    text_parts = []
    metadata = {"page_count": 0, "extraction_method": "pypdf2", "warnings": []}

    try:
        with open(file_path, 'rb') as f:
            reader = PyPDF2.PdfReader(f)
            metadata["page_count"] = len(reader.pages)

            for i, page in enumerate(reader.pages):
                page_text = page.extract_text() or ""
                if len(page_text.strip()) < 10:
                    metadata["warnings"].append(
                        f"Page {i+1}: low text content (possibly image-heavy)"
                    )
                text_parts.append(page_text)

    except Exception as e:
        logger.warning(f"PyPDF2 failed, trying pdfplumber: {e}")
        try:
            import pdfplumber
            metadata["extraction_method"] = "pdfplumber"
            with pdfplumber.open(file_path) as pdf:
                metadata["page_count"] = len(pdf.pages)
                for i, page in enumerate(pdf.pages):
                    page_text = page.extract_text() or ""
                    text_parts.append(page_text)
        except Exception as e2:
            raise RuntimeError(f"All PDF extractors failed: {e2}") from e2

    full_text = "\n\n".join(text_parts)

    if len(full_text.strip()) < 50:
        metadata["warnings"].append("Very low text content — document may be primarily images/scans")

    return full_text, metadata

def extract_markdown(file_path: str) -> tuple[str, dict]:
    """Extract text from markdown files (keep as-is, markdown is already text)."""
    text = Path(file_path).read_text(encoding='utf-8')
    metadata = {
        "extraction_method": "raw_read",
        "has_frontmatter": text.startswith("---"),
        "heading_count": text.count("\n#")
    }
    return text, metadata

def extract_text_file(file_path: str) -> tuple[str, dict]:
    """Extract text from plain text files."""
    text = Path(file_path).read_text(encoding='utf-8')
    metadata = {
        "extraction_method": "raw_read",
        "line_count": text.count("\n") + 1
    }
    return text, metadata
