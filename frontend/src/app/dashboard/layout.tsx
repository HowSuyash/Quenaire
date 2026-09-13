'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { QuenaireMark } from '@/components/brand/QuenaireMark';
import { useRouter as useNextRouter } from 'next/navigation';
import { isAuthenticated, getUser, removeToken } from '@/lib/auth';
import { cn } from '@/lib/utils';
import { 
  LayoutDashboard, 
  FileText, 
  ClipboardList, 
  Settings, 
  LogOut, 
  User, 
  ChevronDown,
  Menu,
  X,
  Search,
  ExternalLink,
  Inbox,
  BarChart3,
  Palette,
  ShieldCheck
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { ToastProvider } from '@/hooks/useToast';

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Questionnaires', href: '/dashboard/questionnaires', icon: ClipboardList },
  { name: 'Review Queue', href: '/dashboard/review-queue', icon: Inbox },
  { name: 'Documents', href: '/dashboard/documents', icon: FileText },
  { name: 'Program', href: '/dashboard/program', icon: ShieldCheck },
  { name: 'Analytics', href: '/dashboard/analytics', icon: BarChart3 },
  { name: 'Branding', href: '/dashboard/branding', icon: Palette },
  { name: 'Settings', href: '/dashboard/settings', icon: Settings },
];

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [user, setUserState] = useState<{ name: string; email: string; role: string } | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push('/login');
      return;
    }
    const u = getUser();
    if (u) {
      setUserState({ name: `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email, email: u.email, role: u.role });
    }
  }, [router]);

  const handleLogout = () => {
    removeToken();
    router.push('/login');
  };


  return (
    <ToastProvider>
      <div className="min-h-screen bg-gray-50">
        {/* Mobile sidebar backdrop */}
        {sidebarOpen && (
          <div 
            className="fixed inset-0 z-40 bg-black/50 lg:hidden" 
            onClick={() => setSidebarOpen(false)} 
          />
        )}

        {/* Sidebar */}
        <aside
          className={cn(
            'fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-200 lg:translate-x-0',
            sidebarOpen ? 'translate-x-0' : '-translate-x-full'
          )}
        >
          <div className="flex h-16 items-center justify-between px-6 border-b border-gray-200">
            <Link href="/dashboard" className="flex items-center gap-2">
              <QuenaireMark className="h-8 w-8" />
              <span className="text-xl font-bold text-gray-900">Quenaire</span>
            </Link>
            <button
              className="lg:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100"
              onClick={() => setSidebarOpen(false)}
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
            {navigation.map((item) => {
              const isActive = pathname === item.href || pathname.startsWith(item.href + '/');
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-primary-50 text-primary-700'
                      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                  )}
                >
                  <item.icon className="h-5 w-5 flex-shrink-0" />
                  {item.name}
                </Link>
              );
            })}
          </nav>

          <div className="p-4 border-t border-gray-200">
            <Link
              href="/api/swagger-ui.html"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-100 hover:text-gray-900"
            >
              <ExternalLink className="h-5 w-5" />
              API Docs
            </Link>
          </div>
        </aside>

        {/* Main content */}
        <div className="lg:pl-64">
          {/* Top bar */}
          <header className="sticky top-0 z-30 bg-white border-b border-gray-200">
            <div className="flex items-center justify-between h-16 px-4 sm:px-6">
              <button
                className="lg:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100"
                onClick={() => setSidebarOpen(true)}
              >
                <Menu className="h-6 w-6" />
              </button>

              <div className="flex-1 lg:flex-none">
                <Input
                  placeholder="Search questionnaires, documents..."
                  className="max-w-md"
                  leftIcon={<Search className="h-4 w-4" />}
                />
              </div>

              <div className="flex items-center gap-4">
                <div className="relative">
                  <button
                    onClick={() => setUserMenuOpen(!userMenuOpen)}
                    className="flex items-center gap-2 p-1 rounded-lg hover:bg-gray-100"
                  >
                    <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center">
                      <User className="h-5 w-5 text-primary-600" />
                    </div>
                    <span className="hidden sm:block text-sm font-medium text-gray-700">{user?.name || 'Signed in'}</span>
                    <ChevronDown className="h-4 w-4 text-gray-500" />
                  </button>

                  {userMenuOpen && (
                    <div className="absolute right-0 mt-2 w-56 bg-white rounded-lg border border-gray-200 shadow-lg py-1 z-50">
                      <div className="px-4 py-2 border-b border-gray-100">
                        <p className="text-sm font-medium text-gray-900">{user?.name}</p>
                        <p className="text-xs text-gray-500">{user?.email}</p>
                        <Badge variant="outline" className="mt-1 text-xs">{user?.role}</Badge>
                      </div>
                      <Link
                        href="/dashboard/settings"
                        className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
                      >
                        <Settings className="h-4 w-4" />
                        Settings
                      </Link>
                      <button
                        onClick={handleLogout}
                        className="flex items-center gap-2 w-full px-4 py-2 text-sm text-red-600 hover:bg-gray-100"
                      >
                        <LogOut className="h-4 w-4" />
                        Sign out
                      </button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </header>

          {/* Page content */}
          <main className="p-4 sm:p-6 lg:p-8">{children}</main>
        </div>
      </div>
    </ToastProvider>
  );
}