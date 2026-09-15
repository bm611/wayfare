/** Read through the server's actual page size, even when it is below our limit. */
export async function loadPages<T>(
  fetchPage: (from: number, to: number) => PromiseLike<{ data: T[] | null; error: { message: string } | null }>,
  signal: AbortSignal,
): Promise<T[]> {
  const rows: T[] = [];
  while (true) {
    signal.throwIfAborted();
    const { data, error } = await fetchPage(rows.length, rows.length + 499);
    signal.throwIfAborted();
    if (error) throw new Error(error.message);
    if (!data?.length) return rows;
    rows.push(...data);
  }
}
