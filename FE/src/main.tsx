import { useState, useEffect } from "react";
import { createRoot } from "react-dom/client";
import {
  QueryClientProvider,
  useMutation,
  useQuery,
} from "@tanstack/react-query";
import {
  RouterProvider,
  createBrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  Archive,
  FolderOpen,
  FilePlus2,
  Layers,
  ShieldCheck,
  FileText,
  ChevronRight,
  CircleCheck,
} from "lucide-react";
import {
  api,
  cache,
  meKey,
  listKey,
  subject,
  month,
  type Summary,
  type User,
} from "./api";
import { Hero, Loading, Problem } from "./ui";
import { Upload } from "./upload";
import { Slip } from "./slip";
import "./style.css";

function Consent({ next }: { next: string }) {
  const [checked, setChecked] = useState(false);
  const nav = useNavigate();
  const agree = useMutation({
    mutationFn: () =>
      api<User>("users/me/terms-agreement", "POST", {
        termsVersion: "2026-11-v1",
      }),
    onSuccess: (u) => {
      cache.setQueryData(meKey(), u);
      nav(next, { replace: true });
    },
  });
  return (
    <>
      <Hero
        title="명세서를 올리기 전에"
        subtitle="급여명세서를 읽고 보관하는 방법을 확인해 주세요."
      />
      <section className="card">
        <div className="section-head">
          <ShieldCheck />
          <h2>급여명세서 처리 동의</h2>
        </div>
        <div className="info-row">
          <FileText />
          <div>
            <strong>원본 파일은 저장하지 않아요</strong>
            <p>
              항목을 읽을 때만 파일을 사용해요. PDF 비밀번호도 보관하지 않아요.
            </p>
          </div>
        </div>
        <div className="info-row">
          <ShieldCheck />
          <div>
            <strong>금액은 암호화해 저장해요</strong>
            <p>읽은 항목을 보관하고, 확인하면 수정한 금액을 저장해요.</p>
          </div>
        </div>
        <details>
          <summary>데모 이용 약관 보기</summary>
          <p>
            급여명세서에서 회사명, 귀속월, 항목과 금액을 추출해 이 브라우저에서
            다시 볼 수 있도록 저장합니다. 데모에는 예시 명세서를 사용해 주세요.
          </p>
          <small>버전 2026-11-v1</small>
        </details>
        <label className="check">
          <input
            type="checkbox"
            checked={checked}
            onChange={(e) => setChecked(e.target.checked)}
          />{" "}
          급여명세서 처리에 동의합니다
        </label>
        <Problem error={agree.error} />
        <button
          className="primary"
          disabled={!checked || agree.isPending}
          onClick={() => agree.mutate()}
        >
          {agree.isPending ? "동의 저장 중" : "동의하고 계속하기"}
        </button>
      </section>
    </>
  );
}
function List() {
  const list = useQuery({
    queryKey: listKey(),
    queryFn: () => api<Summary[]>("payslips"),
  });
  return (
    <>
      <Hero
        title="내 명세서 보관함"
        subtitle="월별로 모아 둔 급여명세서를 확인하세요."
        folder
      />
      <section className="card">
        <div className="section-head">
          <h2>저장된 명세서</h2>
          {list.data && <b className="pill">{list.data.length}건</b>}
          <Link className="primary push" to="/upload">
            <FilePlus2 size={17} /> 명세서 올리기
          </Link>
        </div>
        {list.isPending ? (
          <Loading />
        ) : list.error ? (
          <Problem error={list.error} retry={list.refetch} />
        ) : list.data?.length ? (
          list.data.map((d) => (
            <Link className="list-row" key={d.id} to={"/payslips/" + d.id}>
              <span className="iconbox">
                <FileText />
              </span>
              <div>
                <strong>{month(d.payPeriod)}</strong>
                <p>{d.companyName || "회사명 미표기"}</p>
              </div>
              <small className="push">
                {new Intl.DateTimeFormat("ko-KR", {
                  timeZone: "Asia/Seoul",
                }).format(new Date(d.createdAt))}{" "}
                등록
              </small>
              <ChevronRight size={18} />
            </Link>
          ))
        ) : (
          <div className="empty">
            <FolderOpen size={38} />
            <h3>아직 올린 명세서가 없어요</h3>
            <p>첫 명세서를 올리면 여기에 보관돼요.</p>
            <Link to="/upload" className="secondary">
              첫 명세서 올리기
            </Link>
          </div>
        )}
      </section>
    </>
  );
}
function App() {
  const { pathname, state } = useLocation();
  const me = useQuery({
    queryKey: meKey(),
    queryFn: () => api<User>("users/me"),
  });
  const agree = me.data?.hasConsented;
  const next =
    typeof state?.from === "string" &&
    /^\/(upload|payslips(?:\/\d+(?:\/review)?)?)$/.test(state.from)
      ? state.from
      : "/upload";
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  let page;
  if (me.isPending) page = <Loading />;
  else if (me.error)
    page = (
      <section className="card">
        <h1>내 정보를 불러오지 못했어요</h1>
        <Problem error={me.error} retry={me.refetch} />
      </section>
    );
  else if (!agree && pathname != "/consent")
    page = <Navigate to="/consent" replace state={{ from: pathname }} />;
  else
    page = (
      <Routes>
        <Route path="/" element={<Navigate replace to="/payslips" />} />
        <Route
          path="/consent"
          element={
            agree ? (
              <Navigate replace to={state?.from ? next : "/payslips"} />
            ) : (
              <Consent next={next} />
            )
          }
        />
        <Route path="/upload" element={<Upload />} />
        <Route path="/payslips" element={<List />} />
        <Route path="/payslips/:id" element={<Slip review={false} />} />
        <Route path="/payslips/:id/review" element={<Slip review />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  return (
    <>
      <header>
        <div className="bar">
          <Link to="/" className="brand">
            <span>
              <Layers />
            </span>
            월급서랍
          </Link>
          {agree && (
            <nav>
              <Link
                aria-current={
                  pathname.startsWith("/payslips") ? "page" : undefined
                }
                to="/payslips"
              >
                <Archive />내 명세서
              </Link>
              <Link
                aria-current={pathname === "/upload" ? "page" : undefined}
                to="/upload"
              >
                <FilePlus2 />
                업로드
              </Link>
            </nav>
          )}
        </div>
      </header>
      <main>{page}</main>
      <footer>
        <CircleCheck size={16} /> 월급서랍 · 급여명세서 보관함
      </footer>
    </>
  );
}
function Boot() {
  try {
    subject();
  } catch (e) {
    return <Problem error={e as Error} retry={() => location.reload()} />;
  }
  return <App />;
}
const router = createBrowserRouter([{ path: "*", element: <Boot /> }]);
createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={cache}>
    <RouterProvider router={router} />
  </QueryClientProvider>,
);
