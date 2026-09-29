// 응답 정규화 도우미.
//
// 레거시(HTML)든 이관 후 API(JSON)든 같은 모양으로 바꾼다:
//   { status: <HTTP 상태 코드>, rows: [...], count: <건수>, message: <안내 문구 | null> }
//
// - HTML: <table id="items"> 는 고정 열(id, title, unit, level, tags) 로 읽는다.
//         그 밖의 <table id="..."> 는 머리글(<th>)을 영문 소문자 필드명으로 옮겨 읽는다.
//         건수는 <p id="count">, 안내 문구는 <p id="message"> 에서 뽑는다.
// - JSON: { items, count, message } 를 그대로 쓴다. 배열이 오면 rows 로 본다.
// - 실행할 때마다 바뀌는 값(시각 · 세션 · 토큰 · 요청 ID)은 지운다.
// - rows 의 순서는 응답 순서 그대로 둔다. tags 도 정렬하지 않는다.
import * as cheerio from 'cheerio';

/** 실행마다 달라져 스냅샷에 남기면 안 되는 필드 이름(JSON 응답에서 재귀적으로 제거). */
export const VOLATILE_KEYS = new Set([
  'timestamp',
  'time',
  'now',
  'generatedat',
  'generated_at',
  'servedat',
  'served_at',
  'requestid',
  'request_id',
  'sessionid',
  'session_id',
  'session',
  'token',
  'csrf',
  'elapsed',
  'elapsedms',
  'elapsed_ms',
  'duration',
  'durationms',
  'duration_ms',
]);

/** HTML 표의 머리글(한글 · 영문)을 영문 소문자 필드명으로 옮기는 표. */
export const HEADER_MAP = {
  id: 'id',
  번호: 'id',
  아이디: 'id',
  문항번호: 'id',
  '문항 번호': 'id',
  title: 'title',
  제목: 'title',
  문항: 'title',
  문항제목: 'title',
  '문항 제목': 'title',
  unit: 'unit',
  단원: 'unit',
  단원코드: 'code',
  '단원 코드': 'code',
  code: 'code',
  코드: 'code',
  name: 'name',
  이름: 'name',
  단원명: 'name',
  '단원 이름': 'name',
  grade: 'grade',
  학년: 'grade',
  level: 'level',
  난이도: 'level',
  tags: 'tags',
  태그: 'tags',
  status: 'status',
  상태: 'status',
  count: 'count',
  건수: 'count',
  문항수: 'item_count',
  '문항 수': 'item_count',
  '공개 문항 수': 'item_count',
  '공개 문항수': 'item_count',
  item_count: 'item_count',
  'item count': 'item_count',
  stem: 'stem',
  지문: 'stem',
};

const ITEMS_COLUMNS = ['id', 'title', 'unit', 'level', 'tags'];

function text($el) {
  return $el.text().replace(/\s+/g, ' ').trim();
}

function toNumberIfInteger(value) {
  return /^-?\d+$/.test(value) ? Number(value) : value;
}

function splitTags(value) {
  return value
    .split(',')
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

function parseCount(value) {
  if (value == null) return null;
  const m = String(value).match(/-?\d+/);
  return m ? Number(m[0]) : null;
}

/** 머리글 문자열 → 영문 소문자 필드명. 모르는 머리글은 col1, col2 … 로 둔다. */
export function headerToKey(header, index) {
  const raw = header.replace(/\s+/g, ' ').trim();
  const lowered = raw.toLowerCase();
  if (HEADER_MAP[raw]) return HEADER_MAP[raw];
  if (HEADER_MAP[lowered]) return HEADER_MAP[lowered];
  if (/^[a-z][a-z0-9 _-]*$/.test(lowered)) return lowered.replace(/[\s-]+/g, '_');
  return `col${index + 1}`;
}

/** JSON 값에서 휘발성 필드를 재귀적으로 지운다(배열 · 객체 모두). */
export function stripVolatile(value) {
  if (Array.isArray(value)) return value.map(stripVolatile);
  if (value && typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) {
      if (VOLATILE_KEYS.has(k.toLowerCase())) continue;
      out[k] = stripVolatile(v);
    }
    return out;
  }
  return value;
}

/** HTML 문자열 → {status, rows, count, message, warnings} */
export function normalizeHtml(html, status) {
  const $ = cheerio.load(html);

  let rows = [];
  const items = $('table#items');
  if (items.length > 0) {
    rows = items
      .find('tbody tr')
      .toArray()
      .map((tr) => {
        const cells = $(tr).find('td').toArray().map((td) => text($(td)));
        const row = {};
        ITEMS_COLUMNS.forEach((key, i) => {
          const cell = cells[i] ?? '';
          if (key === 'tags') row[key] = splitTags(cell);
          else if (key === 'id' || key === 'level') row[key] = toNumberIfInteger(cell);
          else row[key] = cell;
        });
        return row;
      });
  } else {
    const table = $('table[id]').first();
    if (table.length > 0) {
      const headers = table
        .find('thead th, thead td')
        .toArray()
        .map((th) => text($(th)));
      const headerRow = headers.length > 0 ? headers : table.find('tr').first().find('th').toArray().map((th) => text($(th)));
      const keys = headerRow.map(headerToKey);
      const bodyRows = headers.length > 0 ? table.find('tbody tr') : table.find('tr').has('td');
      rows = bodyRows
        .toArray()
        .map((tr) => {
          const cells = $(tr).find('td').toArray().map((td) => text($(td)));
          const row = {};
          cells.forEach((cell, i) => {
            const key = keys[i] ?? `col${i + 1}`;
            row[key] = key === 'tags' ? splitTags(cell) : toNumberIfInteger(cell);
          });
          return row;
        });
    }
  }

  const countEl = $('#count');
  const count = countEl.length > 0 ? parseCount(text(countEl)) : rows.length;

  const messageEl = $('#message');
  const messageText = messageEl.length > 0 ? text(messageEl) : '';
  const message = messageText.length > 0 ? messageText : null;

  const warningEls = $('ul#warnings li');
  const warnings = warningEls.toArray().map((li) => text($(li)));

  return { status, rows, count, message, warnings };
}

/** JSON 본문(파싱된 값) → {status, rows, count, message, warnings} */
export function normalizeJson(body, status) {
  const data = stripVolatile(body);
  if (Array.isArray(data)) {
    return { status, rows: data, count: data.length, message: null, warnings: [] };
  }
  if (data && typeof data === 'object') {
    const rows = Array.isArray(data.items) ? data.items : Array.isArray(data.rows) ? data.rows : [];
    const count = typeof data.count === 'number' ? data.count : parseCount(data.count) ?? rows.length;
    const message = data.message == null || data.message === '' ? null : String(data.message);
    const warnings = Array.isArray(data.warnings) ? data.warnings.map(String) : [];
    return { status, rows, count, message, warnings };
  }
  return { status, rows: [], count: 0, message: data == null ? null : String(data), warnings: [] };
}

/**
 * fetch Response → {status, rows, count, message}
 * content-type 이 JSON 이면 JSON 경로, 아니면 HTML 경로로 간다.
 */
export async function normalize(response) {
  const status = response.status;
  const contentType = (response.headers.get('content-type') || '').toLowerCase();
  const body = await response.text();

  if (contentType.includes('json')) {
    let parsed;
    try {
      parsed = body.length > 0 ? JSON.parse(body) : null;
    } catch {
      return { status, rows: [], count: 0, message: body.trim() || null };
    }
    return normalizeJson(parsed, status);
  }
  return normalizeHtml(body, status);
}
