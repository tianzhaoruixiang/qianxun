import request from '@/utils/request'

export interface McpServerItem {
  name: string
  command?: string
  args?: string[]
  env?: Record<string, string>
  enabled: boolean
  description?: string
  transport?: string
  url?: string
}

export interface PluginItem {
  name: string
  path?: string
  version?: string
  enabled: boolean
  description?: string
  manifest?: Record<string, unknown>
}

export function listMcpServers(profile: string): Promise<McpServerItem[]> {
  return request.post('/hermes/mcp/list', { jsonArg: { profile } })
}

export function upsertMcpServer(body: Partial<McpServerItem> & { profile?: string; name: string }): Promise<McpServerItem> {
  return request.post('/hermes/mcp/upsert', { jsonArg: body })
}

export function toggleMcpServer(profile: string, name: string, enabled: boolean): Promise<{ ok: boolean; name: string; enabled: boolean }> {
  return request.post('/hermes/mcp/toggle', { jsonArg: { profile, name, enabled } })
}

export function deleteMcpServer(profile: string, name: string): Promise<void> {
  return request.post('/hermes/mcp/delete', { jsonArg: { profile, name } })
}

export function listPlugins(profile: string): Promise<PluginItem[]> {
  return request.post('/hermes/plugins/list', { jsonArg: { profile } })
}

export function upsertPlugin(body: Partial<PluginItem> & { profile?: string; name: string }): Promise<PluginItem> {
  return request.post('/hermes/plugins/upsert', { jsonArg: body })
}

export function deletePlugin(profile: string, name: string): Promise<void> {
  return request.post('/hermes/plugins/delete', { jsonArg: { profile, name } })
}

export function togglePlugin(profile: string, name: string, enabled: boolean): Promise<{ ok: boolean; name: string; enabled: boolean }> {
  return request.post('/hermes/plugins/toggle', { jsonArg: { profile, name, enabled } })
}

export interface GatewayStatus {
  ok: boolean
  runner: string
  configured: boolean
  model?: string
  authRequired: boolean
}

export function getGatewayStatus(): Promise<GatewayStatus> {
  return request.get('/hermes/gateway/status')
}

export function getDelegation(profile: string, delegationId: string) {
  return request.post('/hermes/delegation/get', { jsonArg: { profile, delegationId } })
}

export function deleteDelegation(profile: string, delegationId: string) {
  return request.post('/hermes/delegation/delete', { jsonArg: { profile, delegationId } })
}

export function cancelDelegation(profile: string, delegationId: string) {
  return request.post('/hermes/delegation/cancel', { jsonArg: { profile, delegationId } })
}
