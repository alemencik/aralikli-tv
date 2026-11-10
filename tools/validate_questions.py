#!/usr/bin/env python3
"""Offline stdlib validator for the Android TV question bank.
This validates structural correctness, not historical accuracy against PDFs.
"""
import json
from pathlib import Path
import sys

DEFAULT = Path(__file__).resolve().parent.parent / 'app/src/main/assets/questions.json'


def validate(path: Path):
    data = json.loads(path.read_text(encoding='utf-8'))
    errors = []
    if data.get('schema') != 1:
        errors.append('schema must be 1')
    decks = data.get('decks')
    if not isinstance(decks, list) or not decks:
        raise ValueError('decks must be nonempty list')
    ids = set()
    deck_ids = set()
    total = 0
    by_deck = {}
    for deck in decks:
        name = deck.get('title', '?')
        did = deck.get('id')
        if not isinstance(did, str) or not did.strip() or did in deck_ids:
            errors.append(f'duplicate/empty deck id {did!r}')
        deck_ids.add(did)
        qs = deck.get('questions')
        if not isinstance(qs, list):
            errors.append(f'{name}: questions must be list')
            continue
        by_deck[did] = len(qs)
        for index, q in enumerate(qs, 1):
            total += 1
            context = f'{name} #{index}'
            qid = q.get('id')
            if not isinstance(qid, str) or not qid.strip() or qid in ids:
                errors.append(f'{context}: duplicate/empty question id {qid!r}')
            ids.add(qid)
            if not isinstance(q.get('stem'), str) or not q['stem'].strip():
                errors.append(f'{context}: empty stem')
            opts = q.get('options')
            if not isinstance(opts, dict) or list(opts.keys()) != ['A', 'B', 'C', 'D', 'E']:
                errors.append(f'{context}: options must be A..E in order')
            elif any(not isinstance(v, str) or not v.strip() for v in opts.values()):
                errors.append(f'{context}: empty option')
            if not isinstance(q.get('answer'), str) or q['answer'] not in 'ABCDE':
                errors.append(f'{context}: missing/unexpected answer')
            if not isinstance(q.get('source_file'), str) or not q['source_file'].strip():
                errors.append(f'{context}: missing source_file')
            if not isinstance(q.get('source_page'), int) or q['source_page'] <= 0:
                errors.append(f'{context}: invalid source_page')
    if total < 116:
        errors.append(f'Question bank lost entries: expected >=116, found {total}')
    if by_deck.get('rapor', 0) < 38 or by_deck.get('yerel', 0) < 78:
        errors.append('Source-deck coverage regression')
    print(f'{total} questions across {len(decks)} decks: {by_deck}')
    if errors:
        print('\n'.join('ERROR: '+error for error in errors), file=sys.stderr)
        raise SystemExit(1)
    print('Structure: OK (source/page visual correctness not verified)')


if __name__ == '__main__':
    validate(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT)
