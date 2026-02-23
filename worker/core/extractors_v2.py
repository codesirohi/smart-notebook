import logging
from typing import List, Optional
from pydantic import BaseModel, Field
from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import PromptTemplate
from llm_factory import LLMFactory

logger = logging.getLogger(__name__)

class DocumentMetadata(BaseModel):
    title: str = Field(description="The title of the document")
    summary: str = Field(description="A brief summary of the document content")
    topics: List[str] = Field(description="List of main topics or keywords")

def extract_metadata(text: str, model_name: str) -> dict:
    """
    Extracts structured metadata using LLMFactory (via LangChain).
    """
    try:
        # We only use the first 2000 chars for metadata extraction to save time
        context = text[:2000]
        
        provider = LLMFactory.get_provider_for_model(model_name)
        llm = LLMFactory.create_chat_model(provider, model_name, temperature=0)
        
        parser = PydanticOutputParser(pydantic_object=DocumentMetadata)
        
        prompt = PromptTemplate(
            template="Extract metadata from the following text.\n{format_instructions}\n\nText:\n{text}\n",
            input_variables=["text"],
            partial_variables={"format_instructions": parser.get_format_instructions()}
        )
        
        chain = prompt | llm | parser
        
        logger.info(f"Extracting metadata using {model_name}...")
        metadata = chain.invoke({"text": context})
        
        return metadata.dict()
        
    except Exception as e:
        logger.warning(f"Metadata extraction failed: {e}")
        return {
            "title": "Unknown Title",
            "summary": "Metadata extraction failed.",
            "topics": []
        }
