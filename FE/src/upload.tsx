import { useEffect, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { FileUp, FileText, LockKeyhole, UploadCloud } from "lucide-react";
import { api, ApiError, remember, type Detail } from "./api";
import { Hero, Modal, Problem } from "./ui";

const manualCodes = [
  "NOT_A_PAYSLIP",
  "LOW_QUALITY_IMAGE",
  "OCR_FAILED",
  "OCR_UNAVAILABLE",
];
let consentRetry: { file: File | null; period: string } | undefined;
export function Upload() {
  const nav = useNavigate();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(consentRetry?.file ?? null);
  const [period, setPeriod] = useState(consentRetry?.period ?? "");
  const [password, setPassword] = useState("");
  const [dialog, setDialog] = useState<"password" | "manual" | null>(null);
  const [needsMonth, setNeedsMonth] = useState(false);
  const [localError, setLocalError] = useState<Error | null>(null);
  const [drag, setDrag] = useState(false);
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    consentRetry = undefined;
  }, []);
  const send = useMutation({
    mutationFn: async (manual: boolean) => {
      if (manual)
        return api<Detail>("payslips/manual", "POST", { payPeriod: period });
      if (!file) throw new Error("파일을 선택해 주세요.");
      const data = new FormData();
      data.append("file", file);
      if (period) data.append("payPeriod", period);
      if (password) data.append("pdfPassword", password);
      return api<Detail>("payslips", "POST", data);
    },
    onSuccess: (d) => {
      remember(d);
      nav("/payslips/" + d.id + "/review", { replace: true });
    },
    onError: (e, manual) => {
      if (!(e instanceof ApiError)) return;
      if (e.code === "TERMS_NOT_AGREED") consentRetry = { file, period };
      if (manual && e.code === "INVALID_INPUT") {
        setDialog("manual");
        setLocalError(e);
      }
      if (e.code === "PAY_PERIOD_REQUIRED") setNeedsMonth(true);
      if (e.code.startsWith("PDF_PASSWORD_")) {
        setPassword("");
        setDialog("password");
      }
    },
  });
  const busy = send.isPending;
  useEffect(() => {
    if (!busy) return;
    const start = Date.now();
    const timer = setInterval(
      () => setSeconds(Math.floor((Date.now() - start) / 1000)),
      500,
    );
    return () => clearInterval(timer);
  }, [busy]);
  function select(files: FileList | null) {
    if (!files?.length || busy) return;
    send.reset();
    setDialog(null);
    setPassword("");
    setPeriod("");
    setNeedsMonth(false);
    const f = files[0];
    const issue =
      files.length !== 1
        ? "파일 한 개를 선택해 주세요."
        : !f.size
          ? "파일이 비어 있어요."
          : f.size > 10 * 1024 * 1024
            ? "10MB 이하의 파일을 선택해 주세요."
            : !(
                  /\.(png|jpe?g|pdf)$/i.test(f.name) ||
                  ["image/png", "image/jpeg", "application/pdf"].includes(
                    f.type,
                  )
                )
              ? "PNG, JPG, PDF 파일만 올릴 수 있어요."
              : null;
    setFile(issue ? null : f);
    setLocalError(issue ? new Error(issue) : null);
  }
  function submit(manual = false) {
    if (busy) return;
    if ((manual || needsMonth) && !/^\d{4}-(0[1-9]|1[0-2])$/.test(period)) {
      setLocalError(new Error("귀속월을 입력해 주세요."));
      return;
    }
    setLocalError(null);
    setDialog(null);
    setSeconds(0);
    send.mutate(manual);
  }
  const code = send.error instanceof ApiError ? send.error.code : "";
  const replace = [
    "INVALID_FILE",
    "NOT_A_PAYSLIP",
    "LOW_QUALITY_IMAGE",
  ].includes(code);
  const monthInput = (
    <label className="form-label">
      귀속월
      <input
        type="month"
        value={period}
        required
        onChange={(e) => setPeriod(e.target.value)}
      />
    </label>
  );
  return (
    <>
      <Hero
        title="급여명세서 올리기"
        subtitle="파일을 올리면 지급·공제 항목을 읽어 정리해요."
      />
      <section className="card">
        <div className="section-head">
          <FileUp />
          <h2>명세서 파일</h2>
        </div>
        <input
          type="file"
          accept=".pdf,.png,.jpg,.jpeg"
          hidden
          ref={input}
          onChange={(e) => {
            select(e.target.files);
            e.target.value = "";
          }}
        />
        <button
          className={"drop " + (drag ? "over" : "")}
          disabled={busy}
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);
            select(e.dataTransfer.files);
          }}
          onClick={() => input.current?.click()}
        >
          {file ? <FileText size={34} /> : <UploadCloud size={38} />}
          <strong>
            {file ? file.name : "파일을 끌어오거나 눌러서 선택하세요"}
          </strong>
          <span>
            {file
              ? Math.ceil(file.size / 1024) + " KB · 다른 파일 선택"
              : "PNG, JPG, PDF · 최대 10MB"}
          </span>
        </button>
        <p className="trust">
          <LockKeyhole size={15} />
          원본 파일은 저장하지 않아요.
        </p>
        {needsMonth && dialog !== "manual" && monthInput}
        {busy ? (
          <div className="progress" role="status">
            {send.variables ? (
              "직접 입력할 명세서를 준비하고 있어요"
            ) : (
              <>
                <div>
                  {["파일 확인", "글자 인식", "항목 정리"].map((s, i) => (
                    <span
                      key={s}
                      className={
                        i === (seconds < 2 ? 0 : seconds < 7 ? 1 : 2)
                          ? "active"
                          : ""
                      }
                    >
                      {s}
                    </span>
                  ))}
                </div>
                <p>
                  {seconds < 9
                    ? "보통 9초 정도 걸려요."
                    : "조금 더 걸리고 있어요. 이 화면에서 기다려 주세요."}
                </p>
              </>
            )}
          </div>
        ) : (
          <>
            <Problem error={localError || send.error} />
            <div className="actions">
              {replace ? (
                <button
                  className="primary"
                  onClick={() => input.current?.click()}
                >
                  {code === "LOW_QUALITY_IMAGE"
                    ? "다시 촬영한 파일 선택"
                    : "다른 파일 선택"}
                </button>
              ) : (
                <button
                  className="primary"
                  disabled={!file}
                  onClick={() => submit()}
                >
                  {code === "OCR_UNAVAILABLE"
                    ? "잠시 후 다시 시도"
                    : send.error
                      ? "다시 올리기"
                      : "명세서 읽기"}
                </button>
              )}
              {(manualCodes.includes(code) || send.variables === true) && (
                <button
                  className="secondary"
                  onClick={() => {
                    setLocalError(null);
                    setDialog("manual");
                  }}
                >
                  직접 입력
                </button>
              )}
              {["CONNECTION", "INTERNAL_ERROR"].includes(code) && (
                <Link className="secondary" to="/payslips">
                  저장된 명세서 확인
                </Link>
              )}
            </div>
          </>
        )}
      </section>
      {dialog && (
        <Modal
          title={
            dialog === "manual" ? "직접 입력할 귀속월" : "PDF 비밀번호 입력"
          }
          close={() => {
            setDialog(null);
            setPassword("");
          }}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              submit(dialog === "manual");
            }}
          >
            {dialog === "manual" ? (
              monthInput
            ) : (
              <>
                <p>{send.error?.message}</p>
                <label className="form-label">
                  PDF 비밀번호
                  <input
                    autoFocus
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </label>
              </>
            )}
            <Problem error={localError} />
            <button className="primary">
              {dialog === "manual" ? "입력 시작" : "이 파일로 다시 읽기"}
            </button>
          </form>
        </Modal>
      )}
    </>
  );
}
