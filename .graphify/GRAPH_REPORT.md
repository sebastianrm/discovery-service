# Graph Report - .  (2026-11-09)

## Corpus Check
- Corpus is ~371 words - fits in a single context window. You may not need a graph.

## Summary
- 14 nodes · 10 edges · 4 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output
- Edge kinds: method: 6 · contains: 4


## Input Scope
- Requested: auto
- Resolved: all (source: default-auto)
- Included files: 3 · Candidates: recursive
- Excluded: 0 untracked · 0 ignored · 0 sensitive · 0 missing committed
## God Nodes (most connected - your core abstractions)
1. `DiscoveryServiceIntegrationTest` - 4 edges
2. `DiscoveryServiceApplication` - 2 edges
3. `DiscoveryServiceApplicationTests` - 2 edges
4. `DiscoveryServiceApplication` - 2 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 1 - "Community 1"
Cohesion: 0.67
Nodes (1): DiscoveryServiceApplication

### Community 2 - "Community 2"
Cohesion: 0.67
Nodes (1): DiscoveryServiceApplicationTests

### Community 0 - "Community 0"
Cohesion: 0.40
Nodes (1): DiscoveryServiceIntegrationTest

### Community 3 - "Community 3"
Cohesion: 0.67
Nodes (1): DiscoveryServiceApplication

## Knowledge Gaps
- **Thin community `Community 1`** (1 nodes): `DiscoveryServiceApplication`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 2`** (1 nodes): `DiscoveryServiceApplicationTests`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 0`** (1 nodes): `DiscoveryServiceIntegrationTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 3`** (1 nodes): `DiscoveryServiceApplication`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Not enough signal to generate questions. This usually means the corpus has no AMBIGUOUS edges, no bridge nodes, no INFERRED relationships, and all communities are tightly cohesive. Add more files or run with --mode deep to extract richer edges._