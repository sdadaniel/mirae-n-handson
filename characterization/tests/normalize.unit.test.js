// 정규화 도우미 자체를 검사하는 단위 테스트(서비스 없이 돈다).
// 동작 보존 테스트가 아니라 도우미의 회귀 방지용이다.
import { describe, expect, it } from 'vitest';
import { headerToKey, normalize, normalizeHtml, normalizeJson, stripVolatile } from '../lib/normalize.mjs';
import { baseUrl, buildUrl } from '../lib/target.mjs';

const ITEMS_HTML = `<!doctype html><html><body>
<h1>문항 검색</h1>
<p id="count">검색 결과 2건</p>
<table id="items">
  <thead><tr><th>번호</th><th>제목</th><th>단원</th><th>난이도</th><th>태그</th></tr></thead>
  <tbody>
    <tr><td>12</td><td>분수의 덧셈 문장제</td><td>M5-1</td><td>4</td><td>계산, 문장제</td></tr>
    <tr><td>3</td><td> 소수의 곱셈 </td><td>M5-3</td><td>2</td><td></td></tr>
  </tbody>
</table>
<footer>세션 abc123 · 2026-09-22 10:00:00</footer>
</body></html>`;

const EMPTY_HTML = `<html><body>
<p id="message">검색 결과가 없습니다</p>
<table id="items"><thead><tr><th>번호</th></tr></thead><tbody></tbody></table>
</body></html>`;

const UNITS_HTML = `<html><body>
<table id="units">
  <thead><tr><th>단원 코드</th><th>단원명</th><th>학년</th><th>Item Count</th></tr></thead>
  <tbody>
    <tr><td>M5-1</td><td>분수의 덧셈과 뺄셈</td><td>5</td><td>7</td></tr>
    <tr><td>M6-2</td><td>비와 비율</td><td>6</td><td>0</td></tr>
  </tbody>
</table>
</body></html>`;

const WARNINGS_HTML = `<!doctype html><html><body>
<ul id="warnings" class="error">
  <li>키워드가 100자를 넘어 100자까지만 사용합니다.</li>
  <li>키워드의 % 와 _ 는 와일드카드로 처리됩니다.</li>
</ul>
<p id="count">검색 결과 0건</p>
<p id="message">검색 결과가 없습니다</p>
</body></html>`;

describe('normalizeHtml', () => {
  it('#items 표는 고정 열(id, title, unit, level, tags)로 읽고 tags 는 쉼표로 나눈다', () => {
    const result = normalizeHtml(ITEMS_HTML, 200);
    expect(result).toEqual({
      status: 200,
      rows: [
        { id: 12, title: '분수의 덧셈 문장제', unit: 'M5-1', level: 4, tags: ['계산', '문장제'] },
        { id: 3, title: '소수의 곱셈', unit: 'M5-3', level: 2, tags: [] },
      ],
      count: 2,
      message: null,
      warnings: [],
    });
  });

  it('0건이면 rows 가 비고 #message 가 message 로 들어간다', () => {
    const result = normalizeHtml(EMPTY_HTML, 200);
    expect(result).toEqual({ status: 200, rows: [], count: 0, message: '검색 결과가 없습니다', warnings: [] });
  });

  it('다른 id 의 표는 머리글을 영문 소문자 필드명으로 옮긴다', () => {
    const result = normalizeHtml(UNITS_HTML, 200);
    expect(result).toEqual({
      status: 200,
      rows: [
        { code: 'M5-1', name: '분수의 덧셈과 뺄셈', grade: 5, item_count: 7 },
        { code: 'M6-2', name: '비와 비율', grade: 6, item_count: 0 },
      ],
      count: 2,
      message: null,
      warnings: [],
    });
  });

  it('#warnings 의 각 <li> 를 warnings 배열로 모은다', () => {
    const result = normalizeHtml(WARNINGS_HTML, 200);
    expect(result.warnings).toEqual([
      '키워드가 100자를 넘어 100자까지만 사용합니다.',
      '키워드의 % 와 _ 는 와일드카드로 처리됩니다.',
    ]);
    expect(result.message).toBe('검색 결과가 없습니다');
    expect(result.rows).toEqual([]);
  });

  it('행 순서를 바꾸지 않는다', () => {
    const result = normalizeHtml(ITEMS_HTML, 200);
    expect(result.rows.map((r) => r.id)).toEqual([12, 3]);
  });

  it('표 밖의 세션 · 시각 문자열은 결과에 남지 않는다', () => {
    const json = JSON.stringify(normalizeHtml(ITEMS_HTML, 200));
    expect(json).not.toContain('abc123');
    expect(json).not.toContain('2026-09-22');
  });
});

describe('headerToKey', () => {
  it('한글 · 영문 머리글을 영문 소문자로 옮기고 모르는 것은 colN 으로 둔다', () => {
    expect(headerToKey('난이도', 0)).toBe('level');
    expect(headerToKey('Title', 1)).toBe('title');
    expect(headerToKey('Item Count', 2)).toBe('item_count');
    expect(headerToKey('알 수 없는 열', 3)).toBe('col4');
  });
});

describe('normalizeJson', () => {
  it('{items, count, message} 를 그대로 옮기고 휘발성 필드는 지운다', () => {
    const body = {
      items: [{ id: 1, title: '분수', unit: 'M5-1', level: 3, tags: ['계산'], requestId: 'r-1' }],
      count: 1,
      message: null,
      generatedAt: '2026-09-22T10:00:00Z',
    };
    expect(normalizeJson(body, 200)).toEqual({
      status: 200,
      rows: [{ id: 1, title: '분수', unit: 'M5-1', level: 3, tags: ['계산'] }],
      count: 1,
      message: null,
      warnings: [],
    });
  });

  it('배열이 오면 rows 로 보고 count 는 길이다', () => {
    expect(normalizeJson([{ code: 'M5-1' }, { code: 'M5-2' }], 200)).toEqual({
      status: 200,
      rows: [{ code: 'M5-1' }, { code: 'M5-2' }],
      count: 2,
      message: null,
      warnings: [],
    });
  });

  it('JSON 응답의 warnings 배열을 포함한다', () => {
    const body = {
      items: [],
      count: 0,
      message: null,
      warnings: ['단원 코드 형식이 올바르지 않습니다.', '등록되지 않은 단원 코드입니다: Z9-9'],
    };
    expect(normalizeJson(body, 200)).toEqual({
      status: 200,
      rows: [],
      count: 0,
      message: null,
      warnings: ['단원 코드 형식이 올바르지 않습니다.', '등록되지 않은 단원 코드입니다: Z9-9'],
    });
  });

  it('stripVolatile 은 중첩된 객체 · 배열 안까지 지운다', () => {
    expect(stripVolatile({ a: { timestamp: 1, b: [{ token: 'x', c: 2 }] } })).toEqual({ a: { b: [{ c: 2 }] } });
  });
});

describe('normalize(Response)', () => {
  it('content-type 이 JSON 이면 JSON 경로', async () => {
    const res = new Response(JSON.stringify({ items: [], count: 0, message: '없음' }), {
      status: 200,
      headers: { 'content-type': 'application/json; charset=utf-8' },
    });
    expect(await normalize(res)).toEqual({ status: 200, rows: [], count: 0, message: '없음', warnings: [] });
  });

  it('content-type 이 HTML 이면 HTML 경로, 상태 코드는 status 에', async () => {
    const res = new Response(EMPTY_HTML, { status: 400, headers: { 'content-type': 'text/html; charset=utf-8' } });
    expect(await normalize(res)).toEqual({ status: 400, rows: [], count: 0, message: '검색 결과가 없습니다', warnings: [] });
  });
});

describe('target', () => {
  it('TARGET_BASE_URL 이 없으면 모듈별 기본 주소를 쓴다', () => {
    const saved = process.env.TARGET_BASE_URL;
    delete process.env.TARGET_BASE_URL;
    try {
      expect(baseUrl('item-bank')).toMatch(/^http:\/\/localhost:\d+$/);
      expect(() => baseUrl('없는모듈')).toThrow(/모듈명/);
    } finally {
      if (saved !== undefined) process.env.TARGET_BASE_URL = saved;
    }
  });

  it('TARGET_BASE_URL 이 있으면 그 주소를 쓰고, 빈 파라미터는 빈값으로 보낸다', () => {
    const saved = process.env.TARGET_BASE_URL;
    process.env.TARGET_BASE_URL = 'http://example.test:9999/';
    try {
      expect(buildUrl('item-bank', '/search.php', { q: ' 분수 ', level: '', tag: undefined })).toBe(
        'http://example.test:9999/search.php?q=+%EB%B6%84%EC%88%98+&level=',
      );
    } finally {
      if (saved === undefined) delete process.env.TARGET_BASE_URL;
      else process.env.TARGET_BASE_URL = saved;
    }
  });
});
