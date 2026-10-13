// 동작 보존 테스트 — 문항 은행(item-bank) 문항 검색 화면 search.php
//
// 대상 주소: lib/target.mjs (환경 변수 TARGET_BASE_URL, 없으면 모듈의 레거시 기본 주소)
// 케이스: 정상 3, 경계값 4, 빈값·누락 2, 이상한 값 3 (합계 12)
// 스냅샷 생성: npm run baseline -- item-bank
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';

describe('item-bank · 문항 검색(search.php)', () => {
  it('기본 호출 — 파라미터 없음(BR-01·BR-14·BR-20·BR-26)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php');
    expect(result).toMatchSnapshot();
  });

  it('단원·난이도 AND 조합 — unit=M5-1·level=3(BR-05·BR-11·BR-15)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: 'M5-1', level: '3' });
    expect(result).toMatchSnapshot();
  });

  it('키워드 검색 — q=함수(BR-07·BR-08)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: '함수' });
    expect(result).toMatchSnapshot();
  });

  it('난이도 5 명시 — level=5(BR-14·BR-15 분기점)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { level: '5' });
    expect(result).toMatchSnapshot();
  });

  it('키워드 100자 — BR-07 경계(잘림 조건 미충족)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: 'a'.repeat(100) });
    expect(result).toMatchSnapshot();
  });

  it('키워드 101자 — BR-07 경계(잘림 조건 충족)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: 'a'.repeat(101) });
    expect(result).toMatchSnapshot();
  });

  it('페이지 최댓값 999 — BR-27 상한 경계', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { page: '999' });
    expect(result).toMatchSnapshot();
  });

  it('q 빈 문자열 — BR-07·BR-14 빈값 처리', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: '' });
    expect(result).toMatchSnapshot();
  });

  it('sort 없이 dir만 전달 — BR-20·BR-22 누락 파라미터', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { dir: 'asc' });
    expect(result).toMatchSnapshot();
  });

  it('존재하지 않는 단원 코드(형식 유효) — unit=Z9-9(BR-12·BR-13)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: 'Z9-9' });
    expect(result).toMatchSnapshot();
  });

  it('음수 난이도 level=-1 — BR-16 범위 밖 값', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { level: '-1' });
    expect(result).toMatchSnapshot();
  });

  it('200자 이상 단원 코드 — BR-12·BR-13 이상한 값', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: 'A'.repeat(200) });
    expect(result).toMatchSnapshot();
  });
});
