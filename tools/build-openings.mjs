// Optional maintainer tool. The desktop app only needs the generated JSON.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [source, target] = process.argv.slice(2);
if (!source || !target) throw new Error('Usage: node tools/build-openings.mjs tsv-directory output.json');
const entries = [];
for (const letter of ['a', 'b', 'c', 'd', 'e']) {
  const lines = readFileSync(join(source, `${letter}.tsv`), 'utf8').trim().split(/\r?\n/);
  if (lines.shift() !== 'eco\tname\tpgn') throw new Error('Unexpected upstream TSV schema');
  for (const line of lines) {
    const fields = line.split('\t');
    if (fields.length !== 3) throw new Error('Unexpected TSV fields');
    const [eco, name, pgn] = fields;
    entries.push({ eco, name, pgn });
  }
}
writeFileSync(target, JSON.stringify({
  schemaVersion: 1,
  source: 'https://github.com/lichess-org/chess-openings',
  revision: 'c67912be581f0793dbaa776be5ccf111e01f88d9',
  license: 'CC0-1.0',
  entries,
  statistics: []
}, null, 2) + '\n');
console.log(`Generated ${entries.length} openings; no fabricated frequency statistics.`);
