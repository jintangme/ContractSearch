const trustPriority = { VERIFIED: 0, AUTHORITATIVE: 1, CURATED: 2, TOOL_GENERATED: 3, COMMUNITY: 4, USER_PROVIDED: 5, UNTRUSTED: 6 }

export function trustLevelLabel(level) {
  return {
    VERIFIED: '已验证', AUTHORITATIVE: '权威来源', CURATED: '精选资料',
    COMMUNITY: '社区资料', USER_PROVIDED: '用户提供', TOOL_GENERATED: '工具结果', UNTRUSTED: '待核验',
  }[level] || '参考资料'
}

export function citedEvidenceIds(events = []) {
  const finalAction = [...events].reverse().find(event => {
    const payload = event?.data?.payload
    return event.type === 'model.action' && ['FINAL_ANSWER', 'COMPLETE', 'ABSTAIN'].includes(payload?.actionType)
  })
  return finalAction?.data?.payload?.citedEvidenceIds || []
}

export function evidenceLink(item) {
  const metadata = item.metadata || {}
  for (const candidate of [metadata.sourceUrl, item.sourceUrl, item.sourceRef]) {
    if (typeof candidate !== 'string' || !/^https?:\/\//i.test(candidate)) continue
    try {
      const url = new URL(candidate)
      if (url.username || url.password) continue
      return { href: url.href, external: true }
    } catch { /* Non-URL references are displayed as text. */ }
  }
  const contractId = Number(metadata.contractId || /^contract:(\d+)$/.exec(item.sourceRef)?.[1])
  return Number.isSafeInteger(contractId) && contractId > 0
    ? { href: `/contract/${contractId}`, external: false }
    : null
}

export function prepareEvidence(rawEvidence, citedIds = []) {
  const cited = new Set(citedIds.map(String))
  const grouped = new Map()
  for (const [index, item] of (Array.isArray(rawEvidence) ? rawEvidence : []).entries()) {
    if (!item || typeof item !== 'object') continue
    const metadata = item.metadata || {}
    // A shared title does not imply the same source or the same lines of code.
    const key = JSON.stringify([
      item.sourceType, item.sourceRef, metadata.sourceUrl, metadata.contractId,
      metadata.filePath, metadata.lineStart, metadata.lineEnd, metadata.chunkId,
      metadata.contentHash, item.excerpt, item.trustLevel,
      item.sourceRef || metadata.sourceUrl || metadata.contractId ? null : item.id || index,
    ])
    const existing = grouped.get(key)
    if (existing) {
      existing.relatedCount += 1
      existing.cited ||= cited.has(String(item.id))
      continue
    }
    const lineStart = Number(metadata.lineStart)
    const lineEnd = Number(metadata.lineEnd)
    grouped.set(key, {
      ...item,
      key,
      title: String(item.title || item.sourceType || '参考资料').trim(),
      excerpt: String(item.excerpt || '').trim(),
      sourceRef: String(item.sourceRef || ''),
      location: [metadata.filePath, lineStart > 0 ? `L${lineStart}${lineEnd > lineStart ? `–L${lineEnd}` : ''}` : ''].filter(Boolean).join(' · '),
      link: evidenceLink(item),
      relatedCount: 1,
      cited: cited.has(String(item.id)),
    })
  }
  return [...grouped.values()].sort((a, b) => Number(b.cited) - Number(a.cited)
    || (trustPriority[a.trustLevel] ?? 9) - (trustPriority[b.trustLevel] ?? 9))
}
