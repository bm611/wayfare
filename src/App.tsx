import { lazy, Suspense } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation, Outlet } from "react-router-dom";
import { AuthProvider, useAuth } from "./hooks/useAuth";
import { Brand } from "./components/Brand";

const Auth = lazy(() => import("./routes/Auth").then((module) => ({ default: module.Auth })));
const Trips = lazy(() => import("./routes/Trips").then((module) => ({ default: module.Trips })));
const TripDetail = lazy(() => import("./routes/TripDetail").then((module) => ({ default: module.TripDetail })));
const Join = lazy(() => import("./routes/Join").then((module) => ({ default: module.Join })));

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Suspense fallback={<RouteLoading />}><Routes>
          <Route path="/auth" element={<Auth />} />
          <Route element={<RequireAuth />}>
            <Route index element={<Trips />} />
            <Route path="/trip/:id" element={<TripDetail />} />
            <Route path="/join/:code" element={<Join />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes></Suspense>
      </BrowserRouter>
    </AuthProvider>
  );
}

export const REDIRECT_KEY = "wayfare.redirect";

function RequireAuth() {
  const { session, loading } = useAuth();
  const location = useLocation();

  if (loading) return <RouteLoading />;

  if (!session) {
    // Remember an invite link so signing in lands on the trip, not the list.
    try {
      sessionStorage.setItem(REDIRECT_KEY, location.pathname);
    } catch {
      // Storage disabled — the user just lands on their trip list instead.
    }
    return <Navigate to="/auth" replace />;
  }
  return <Outlet key={session.user.id} />;
}

function RouteLoading() {
  return <div className="grid min-h-[100dvh] place-items-center" role="status" aria-label="Loading">
    <div className="flex flex-col items-center gap-3">
      <Brand />
      <div className="flex gap-1">
        {[0, 1, 2].map((i) => <span key={i} className="beacon size-1.5 rounded-full bg-clay"
          style={{ animationDelay: `${i * 180}ms` }} />)}
      </div>
    </div>
  </div>;
}
