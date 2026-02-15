export enum ModelProvider {
    GOOGLE = 'google',
    ANTHROPIC = 'anthropic',
    OPENAI = 'openai',
    OLLAMA = 'ollama',
}

export const AVAILABLE_MODELS = [
    { provider: ModelProvider.OPENAI, id: 'gpt-3.5-turbo', name: 'GPT-3.5 Turbo' },
    { provider: ModelProvider.OPENAI, id: 'gpt-4', name: 'GPT-4' },
    { provider: ModelProvider.ANTHROPIC, id: 'claude-3-haiku-20240307', name: 'Claude 3 Haiku' },
    { provider: ModelProvider.ANTHROPIC, id: 'claude-3-sonnet-20240229', name: 'Claude 3 Sonnet' },
    { provider: ModelProvider.ANTHROPIC, id: 'claude-3-opus-20240229', name: 'Claude 3 Opus' },
    { provider: ModelProvider.GOOGLE, id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash' },
    { provider: ModelProvider.GOOGLE, id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro' },
    { provider: ModelProvider.OLLAMA, id: 'phi3:mini', name: 'Phi-3 Mini (Local)' },
    { provider: ModelProvider.OLLAMA, id: 'llama3', name: 'Llama 3 (Local)' },
    { provider: ModelProvider.OLLAMA, id: 'tinyllama', name: 'TinyLlama (Local)' },
];
