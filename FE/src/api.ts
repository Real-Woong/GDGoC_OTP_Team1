import { QueryClient } from "@tanstack/react-query";

export const groups = [
  {
    title: "지급 내역",
    keys: [
      "base_salary",
      "meal_allowance",
      "car_allowance",
      "overtime_pay",
      "bonus",
      "payment_total",
    ],
  },
  {
    title: "공제 내역",
    keys: [
      "national_pension",
      "health_insurance",
      "long_term_care",
      "employment_insurance",
      "income_tax",
      "local_income_tax",
      "deduction_total",
    ],
  },
  { title: "실수령액", keys: ["net_pay"] },
] as const;
export type Key = (typeof groups)[number]["keys"][number];
export interface User {
  id: number;
  birthYear: number | null;
  joinedOn: string | null;
  isSmeEmployee: boolean | null;
  termsVersion: string | null;
  consentedAt: string | null;
  hasConsented: boolean;
}
export interface Summary {
  id: number;
  payPeriod: string;
  createdAt: string;
  companyName: string | null;
}
export interface Field {
  key: Key;
  label: string;
  value: number | null;
  confidence: number | null;
  sourceLabel: string | null;
  status: "OK" | "NEEDS_REVIEW" | "MISSING";
  userConfirmed: boolean;
}
export interface Detail extends Summary {
  fields: Field[];
  unmappedItems: { label: string; amount: number | null }[];
  warnings: { field: Key | null; reason: string }[];
  fullyConfirmed: boolean;
}
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public fieldErrors: { field: string; reason: string }[] = [],
  ) {
    super(message);
  }
}
export const cache = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30000, refetchOnWindowFocus: false },
    mutations: { retry: false, gcTime: 0 },
  },
});
export function subject() {
  try {
    let id = localStorage.getItem("otp.subject");
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem("otp.subject", id);
    }
    return id;
  } catch {
    throw new Error("브라우저 저장소를 허용한 뒤 다시 시도해 주세요.");
  }
}
export const detailKey = (id: string | number) => [
  "payslip",
  subject(),
  String(id),
];
export const listKey = () => ["payslips", subject()];
export const meKey = () => ["me", subject()];
const base = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");
export async function api<T>(
  path: string,
  method = "GET",
  body?: FormData | object,
): Promise<T> {
  const headers: Record<string, string> = { "X-User-Subject": subject() };
  const form = body instanceof FormData;
  if (body && !form) headers["Content-Type"] = "application/json";
  let response: Response;
  try {
    response = await fetch(base + "/api/" + path, {
      method,
      headers,
      body: form ? body : JSON.stringify(body),
      signal: AbortSignal.timeout(45000),
    });
  } catch {
    throw new ApiError(
      "CONNECTION",
      "서버 응답을 받지 못했어요. 연결 상태를 확인해 주세요.",
    );
  }
  const data = await response.json().catch(() => {
    throw new ApiError("CONNECTION", "서버 응답을 읽지 못했어요.");
  });
  if (!response.ok) {
    if (data.code === "TERMS_NOT_AGREED")
      cache.setQueryData<User>(
        meKey(),
        (u) => u && { ...u, hasConsented: false },
      );
    throw new ApiError(
      data.code,
      data.message || "요청을 처리하지 못했어요.",
      data.fieldErrors,
    );
  }
  return data;
}
export function remember(d: Detail) {
  cache.setQueryData(detailKey(d.id), d);
  void cache.invalidateQueries({ queryKey: listKey() });
}
export const month = (s: string) =>
  s.slice(0, 4) + "년 " + Number(s.slice(5)) + "월";
export const money = (n: number) => "₩" + n.toLocaleString("ko-KR");
