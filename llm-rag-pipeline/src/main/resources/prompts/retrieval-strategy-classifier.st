Choose the best retrieval strategy for the QUERY below.

Search mode:
  vector: semantic / paraphrased natural-language questions
  keyword: exact terms — error codes, IDs, names
  hybrid: queries that mix natural language and exact terms

Query transform:
  none: query is already a clean, specific search query
  rewrite: query is ungrammatical, abbreviated, or conversationally phrased
  multi_query: broad/ambiguous query that could be phrased several ways
  hyde: sparse or technical domain where a hypothetical answer embeds better than the query
  step_back: overly narrow or multi-hop query that needs broader context first

QUERY: {query}
