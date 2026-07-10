import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import {
  AuthUser,
  Permission,
  Role,
  fetchMe,
  getToken,
  login as apiLogin,
  logout as apiLogout,
} from '../api/client';

interface AuthState {
  user: AuthUser | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  /** Replace the current user in context (after a password change / profile update). */
  setUser: (user: AuthUser) => void;
  /** True if the current user's role is one of the given roles. */
  hasRole: (...roles: Role[]) => boolean;
  /** True if the current user holds the given capability key (from the DB matrix). */
  can: (permission: Permission) => boolean;
  isAdmin: boolean;
  /** May create/edit content records (tasks, journals, planners, …). */
  canWriteContent: boolean;
  /** May create/edit lookup/parameter data (types, statuses, departments, …). */
  canWriteParams: boolean;
  /** May write anything at all (i.e. not a read-only account). */
  canWrite: boolean;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  // On mount, if we hold a token, resolve the current user (validates the token too).
  useEffect(() => {
    if (!getToken()) {
      setLoading(false);
      return;
    }
    fetchMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = async (username: string, password: string) => {
    setUser(await apiLogin(username, password));
  };

  const logout = () => {
    apiLogout();
    setUser(null);
  };

  const hasRole = (...roles: Role[]) => !!user && roles.includes(user.role);
  const can = (permission: Permission) => !!user && user.permissions.includes(permission);
  const isAdmin = hasRole('ADMIN');
  const canWriteContent = can('content.write');
  const canWriteParams = can('params.write');
  const canWrite = canWriteContent || canWriteParams;

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        logout,
        setUser,
        hasRole,
        can,
        isAdmin,
        canWriteContent,
        canWriteParams,
        canWrite,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
