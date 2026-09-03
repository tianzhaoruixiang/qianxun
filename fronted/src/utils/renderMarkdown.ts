import MarkdownIt from 'markdown-it'
import { normalizeMarkdown } from '@/utils/normalizeMarkdown'
import { isChatDocumentExt, rewritePublicFileHref } from '@/utils/chatDocuments'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
})

const defaultLinkOpen = md.renderer.rules.link_open
  ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const hrefIdx = token.attrIndex('href')
  if (hrefIdx >= 0 && token.attrs) {
    token.attrs[hrefIdx][1] = rewritePublicFileHref(token.attrs[hrefIdx][1])
  }
  const href = hrefIdx >= 0 && token.attrs ? token.attrs[hrefIdx][1] : ''
  const filename = href.split('/').pop() || ''
  const ext = filename.includes('.') ? filename.slice(filename.lastIndexOf('.') + 1).toLowerCase() : ''
  const target = token.attrIndex('target')
  if (target < 0) token.attrPush(['target', '_blank'])
  else token.attrs![target][1] = '_blank'
  const rel = token.attrIndex('rel')
  if (rel < 0) token.attrPush(['rel', 'noopener noreferrer'])
  else token.attrs![rel][1] = 'noopener noreferrer'
  if (isChatDocumentExt(ext) || href.includes('/data/files/public/')) {
    const classIdx = token.attrIndex('class')
    if (classIdx < 0) token.attrPush(['class', 'chat-doc-link'])
    else token.attrs![classIdx][1] += ' chat-doc-link'
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const defaultTableOpen = md.renderer.rules.table_open
  ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
const defaultTableClose = md.renderer.rules.table_close
  ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.table_open = (tokens, idx, options, env, self) =>
  `<div class="table-wrap">${defaultTableOpen(tokens, idx, options, env, self)}`
md.renderer.rules.table_close = (tokens, idx, options, env, self) =>
  `${defaultTableClose(tokens, idx, options, env, self)}</div>`

export function renderMarkdown(source: string): string {
  return md.render(normalizeMarkdown(source || ''))
}
