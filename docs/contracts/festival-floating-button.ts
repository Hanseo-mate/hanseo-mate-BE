/** 백엔드 OpenAPI와 동일한 관리자/앱 연동 타입. 이 파일은 UI 구현이 아닌 전달용 계약입니다. */
export type FestivalFloatingButtonAdminResponse = {
  visible: boolean;
  updatedAt: string | null;
};

export type UpdateFestivalFloatingButtonRequest = {
  visible: boolean;
};

/** 기존 HomeResponse에 합성할 최상위 필드입니다. */
export type FestivalFloatingButtonHomeFields = {
  festivalFloatingButtonVisible: boolean;
};

/** 구버전 응답/조회 실패/갱신 실패 시 과거 true를 사용하지 않습니다. */
export function shouldShowFestivalFloatingButton(
  response: unknown,
  queryFailed: boolean,
): boolean {
  return !queryFailed
    && typeof response === "object"
    && response !== null
    && "festivalFloatingButtonVisible" in response
    && response.festivalFloatingButtonVisible === true;
}
