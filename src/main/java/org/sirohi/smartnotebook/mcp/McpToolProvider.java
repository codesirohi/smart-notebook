package org.sirohi.smartnotebook.mcp;

import java.util.List;
import java.util.Map;

/**
 * Abstraction for invoking tools via the Model Context Protocol (MCP).
 *
 * <p>
 * MCP tool calls enrich query context beyond vector retrieval.
 * </p>
 */
public interface McpToolProvider {

    /**
     * Invokes an MCP tool and returns the result.
     *
     * @param toolName   the registered tool name (e.g., "search_notebook")
     * @param parameters tool-specific parameters as key-value pairs
     * @return the tool invocation result as a string
     */
    String invokeTool(String toolName, Map<String, Object> parameters);

    /**
     * Lists available tools from registered MCP servers.
     *
     * @return list of available tool names
     */
    List<String> listAvailableTools();
}
