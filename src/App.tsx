import type { ReactNode } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AuthProvider, useAuth } from "./hooks/useAuth";
import { Auth } from "./routes/Auth";
import { Trips } from "./routes/Trips";
import { TripDetail } from "./routes/TripDetail";
import { Join } from "./routes/Join";
import { Brand } from "./components/Brand";

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/auth" element={<Auth />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <Trips />
              </RequireAuth>
            }
          />
          <Route
            path="/trip/:id"
            element={
              <RequireAuth>
                <TripDetail />
              </RequireAuth>
            }
          />
          <Route
            path="/join/:code"
            element={
              <RequireAuth>
                <Join />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export const REDIRECT_KEY = "wayfare.redirect";

function RequireAuth({ children }: { children: ReactNode }) {
  const { session, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="grid min-h-[100dvh] place-items-center">
        <div className="flex flex-col items-center gap-3">
          <Brand />
          <div className="flex gap-1">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className="beacon size-1.5 rounded-full bg-clay"
                style={{ animationDelay: `${i * 180}ms` }}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (!session) {
    // Remember an invite link so signing in lands on the trip, not the list.
    try {
      sessionStorage.setItem(REDIRECT_KEY, location.pathname);
    } catch {
      // Storage disabled — the user just lands on their trip list instead.
    }
    return <Navigate to="/auth" replace />;
  }
  return <>{children}</>;
}
