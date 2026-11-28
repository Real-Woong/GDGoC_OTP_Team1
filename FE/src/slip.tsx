import { useEffect, useState, useRef } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useBlocker } from "react-router-dom";
import { AlertTriangle, CheckCheck, LockKeyhole, Pencil } from "lucide-react";
import {
  api,
  ApiError,
  detailKey,
  groups,
  money,
  month,
  remember,
  type Detail,
  type Field,
  type Key,
} from "./api";
import { Hero, Loading, Problem } from "./ui";

function Extras({ d }: { d: Detail }) {
  return (
    <>
      {d.unmappedItems.length > 0 && (
        <section className="card">
          <h2>표준 외 항목</h2>
          {d.unmappedItems.map((v, i) => (
            <div className="line" key={i}>
              <span>{v.label}</span>
              <strong>
                {v.amount === null ? "금액 미표기" : money(v.amount)}
              </strong>
            </div>
          ))}
        </section>
      )}
      {d.warnings.length > 0 && (
        <section className="card">
          <h2>인식 당시 안내</h2>
          {d.warnings.map((w, i) => (
            <p key={i}>
              {w.field && (
                <strong>
                  {d.fields.find((f) => f.key === w.field)?.label} ·{" "}
                </strong>
              )}
              {w.reason}
            </p>
          ))}
        </section>
      )}
    </>
  );
}
const emptyText = (f: Field) => (f.userConfirmed ? "항목 없음" : "못 읽었어요");
function Details({ d }: { d: Detail }) {
  const net = d.fields.find((f) => f.key === "net_pay")!;
  return (
    <>
      <Hero
        title={month(d.payPeriod) + " 명세서"}
        subtitle={d.companyName || "회사명 미표기"}
      />
      <section className="card net">
        <div>
          <p>실수령액</p>
          <h2>{net.value === null ? emptyText(net) : money(net.value)}</h2>
        </div>
        <span className="pill">
          {d.fullyConfirmed ? "확인 완료" : "확인 전"}
        </span>
      </section>
      <div className="columns">
        {groups.slice(0, 2).map((g) => (
          <section className="card" key={g.title}>
            <h2>{g.title}</h2>
            {d.fields
              .filter((f) => (g.keys as readonly Key[]).includes(f.key))
              .map((f) => (
                <div className="line" key={f.key}>
                  <span>{f.label}</span>
                  <strong>
                    {f.value === null ? emptyText(f) : money(f.value)}
                  </strong>
                </div>
              ))}
          </section>
        ))}
      </div>
      <Extras d={d} />
      <div className="actions">
        <Link className="primary" to={"/payslips/" + d.id + "/review"}>
          <Pencil size={16} />
          {d.fullyConfirmed ? "값 수정" : "이어서 확인"}
        </Link>
        <Link className="secondary" to="/payslips">
          내 명세서로
        </Link>
      </div>
    </>
  );
}
type Draft = Record<string, string | null>;
function valid(v: string | null) {
  return v === null || (/^\d+$/.test(v) && Number(v) <= 2147483647);
}
function Editor({ d }: { d: Detail }) {
  const nav = useNavigate();
  const [draft, setDraft] = useState<Draft>(() =>
    Object.fromEntries(
      d.fields.map((f) => [
        f.key,
        f.value === null ? (f.userConfirmed ? null : "") : String(f.value),
      ]),
    ),
  );
  const [focus, setFocus] = useState<Key | null>(null);
  const [attempt, setAttempt] = useState(false);
  const [dirty, setDirty] = useState(false);
  const saved = useRef(false);
  useBlocker(
    () =>
      dirty &&
      !saved.current &&
      !window.confirm("저장하지 않은 수정이 있어요. 이 화면을 떠날까요?"),
  );
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const save = useMutation({
    mutationFn: () =>
      api<Detail>("payslips/" + d.id + "/fields", "PATCH", {
        fields: d.fields.map((f) => ({
          key: f.key,
          value: draft[f.key] === null ? null : Number(draft[f.key]),
        })),
      }),
    onSuccess: (updated) => {
      remember(updated);
      saved.current = updated.fullyConfirmed;
      setDirty(false);
      if (updated.fullyConfirmed) nav("/payslips/" + d.id, { replace: true });
    },
  });
  function edit(key: Key, value: string | null) {
    setDirty(true);
    setDraft((prev) => ({ ...prev, [key]: value }));
  }
  const totals = ["payment_total", "deduction_total", "net_pay"].map(
    (k) => draft[k],
  );
  let comparison = "세 금액을 입력하면 합계를 비교할 수 있어요.";
  if (totals.every((v) => v !== null && valid(v))) {
    const [p, c, n] = totals.map(Number);
    comparison =
      money(p) +
      " − " +
      money(c) +
      " = " +
      money(p - c) +
      (p - c === n
        ? " · 실수령액과 일치"
        : " · 입력한 실수령액 " + money(n) + "과 달라요");
  }
  return (
    <>
      <Hero
        title="읽은 값 확인"
        subtitle={
          month(d.payPeriod) + " · " + (d.companyName || "회사명 미표기")
        }
      />
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setAttempt(true);
          const bad = d.fields.find((f) => !valid(draft[f.key]));
          if (bad) {
            document.getElementById(bad.key)?.focus();
            return;
          }
          if (!save.isPending) save.mutate();
        }}
      >
        <fieldset disabled={save.isPending}>
          <div className="columns">
            {groups.map((g) => (
              <section className="card" key={g.title}>
                <h2>{g.title}</h2>
                {d.fields
                  .filter((f) => (g.keys as readonly Key[]).includes(f.key))
                  .map((f) => {
                    const v = draft[f.key];
                    const warning = f.status === "NEEDS_REVIEW";
                    const fieldIssue =
                      save.error instanceof ApiError
                        ? save.error.fieldErrors.find(
                            (e) =>
                              e.field ===
                              "fields[" + d.fields.indexOf(f) + "].value",
                          )?.reason
                        : undefined;
                    const issue =
                      fieldIssue ||
                      (attempt && !valid(v)
                        ? "0 이상의 원 단위 정수를 입력하거나 항목 없음을 선택해 주세요."
                        : "");
                    return (
                      <div
                        className={"edit-row " + (warning ? "warn" : "")}
                        key={f.key}
                      >
                        <div className="field-title">
                          <label htmlFor={f.key}>{f.label}</label>
                          {warning ? (
                            <small className="flag">
                              <AlertTriangle size={13} /> 확인 필요
                            </small>
                          ) : f.status === "MISSING" &&
                            !f.userConfirmed &&
                            v === "" ? (
                            <small>못 읽었어요</small>
                          ) : null}
                        </div>
                        {f.sourceLabel && (
                          <small>명세서 원문 ‘{f.sourceLabel}’</small>
                        )}
                        <div className="amount">
                          <div className="amount-box">
                            <input
                              id={f.key}
                              inputMode="numeric"
                              aria-invalid={!!issue}
                              aria-describedby={
                                issue ? f.key + "-issue" : undefined
                              }
                              placeholder={
                                v === null ? "항목 없음" : "금액 입력"
                              }
                              value={v === null ? "" : v}
                              onFocus={() => setFocus(f.key)}
                              onBlur={() => setFocus(null)}
                              onChange={(e) =>
                                edit(f.key, e.target.value.replaceAll(",", ""))
                              }
                            />
                            {focus !== f.key &&
                              v !== null &&
                              v !== "" &&
                              valid(v) && (
                                <span className="formatted" aria-hidden="true">
                                  {Number(v).toLocaleString("ko-KR")}
                                </span>
                              )}
                          </div>
                          <span>원</span>
                        </div>
                        <label className="absent">
                          <input
                            type="checkbox"
                            checked={v === null}
                            onChange={(e) =>
                              edit(f.key, e.target.checked ? null : "")
                            }
                          />
                          이 항목 없음
                        </label>
                        {issue && (
                          <small className="invalid" id={f.key + "-issue"}>
                            {issue}
                          </small>
                        )}
                      </div>
                    );
                  })}
              </section>
            ))}
          </div>
        </fieldset>
        <Extras d={d} />
        <section className="card summary">
          <h2>지급합계 − 공제합계 = 실수령액</h2>
          <p>{comparison}</p>
          <p className="trust">
            <LockKeyhole size={15} />
            금액은 암호화해 저장해요.
          </p>
          <Problem error={save.error} />
          <button className="primary" disabled={save.isPending}>
            <CheckCheck size={18} />
            {save.isPending ? "저장 중" : "모두 확인"}
          </button>
        </section>
      </form>
    </>
  );
}
export function Slip({ review }: { review: boolean }) {
  const { id = "" } = useParams();
  const q = useQuery({
    queryKey: detailKey(id),
    queryFn: () => api<Detail>("payslips/" + encodeURIComponent(id)),
  });
  if (q.isPending) return <Loading />;
  if (
    q.error ||
    !q.data ||
    !groups.every((g) =>
      g.keys.every((k) => q.data.fields.some((f) => f.key === k)),
    ) ||
    q.data.fields.length !== 14
  )
    return (
      <section className="card">
        <Problem
          error={q.error || new Error("명세서 항목을 읽지 못했어요.")}
          retry={q.refetch}
        />
        <Link to="/payslips">내 명세서로</Link>
      </section>
    );
  return review ? <Editor key={id} d={q.data} /> : <Details d={q.data} />;
}
