import { useEffect, useRef, type ReactNode } from "react";
import { FileText, Check, AlertCircle, LoaderCircle } from "lucide-react";
import { ApiError } from "./api";

export function Hero({
  title,
  subtitle,
  folder = false,
}: {
  title: string;
  subtitle: string;
  folder?: boolean;
}) {
  return (
    <section className="hero">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      <div aria-hidden="true" className={"art " + (folder ? "folder" : "")}>
        <div className="paper back" />
        <div className="paper">
          <FileText size={22} />
          <i />
          <i />
          <i />
        </div>
        <div className="seal">
          <Check />
        </div>
      </div>
    </section>
  );
}
export function Loading() {
  return (
    <div className="notice" role="status">
      <LoaderCircle className="spin" /> 불러오는 중이에요
    </div>
  );
}
export function Problem({
  error,
  retry,
}: {
  error: Error | null;
  retry?: () => void;
}) {
  if (!error) return null;
  return (
    <div role="alert" className="problem">
      <AlertCircle size={19} />
      <div>
        {error.message}
        {error instanceof ApiError &&
          error.fieldErrors.map((e, i) => <small key={i}>{e.reason}</small>)}
        {retry && (
          <button className="text-btn" onClick={retry}>
            다시 시도
          </button>
        )}
      </div>
    </div>
  );
}
export function Modal({
  title,
  close,
  children,
}: {
  title: string;
  close: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog ref={ref} aria-labelledby="dialog-title" onCancel={close}>
      <h2 id="dialog-title">{title}</h2>
      {children}
      <button className="text-btn" onClick={close}>
        취소
      </button>
    </dialog>
  );
}
