from worker.llm_factory import LLMFactory
import unittest
from unittest.mock import MagicMock, patch

class TestLLMFactory(unittest.TestCase):
    def test_provider_resolution(self):
        self.assertEqual(LLMFactory.get_provider_for_model("gpt-4"), "openai")
        self.assertEqual(LLMFactory.get_provider_for_model("claude-3"), "anthropic")
        self.assertEqual(LLMFactory.get_provider_for_model("gemini-pro"), "google")
        self.assertEqual(LLMFactory.get_provider_for_model("llama3"), "ollama")
        
    @patch("worker.llm_factory.ChatOllama")
    def test_create_chat_ollama(self, mock_ollama):
        LLMFactory.create_chat_model("ollama", "llama3")
        mock_ollama.assert_called_once()
        
if __name__ == '__main__':
    unittest.main()
