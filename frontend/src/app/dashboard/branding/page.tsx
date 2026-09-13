'use client';

import { useEffect, useState } from 'react';
import { Loader2, Palette, Save, Globe, Eye } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { useToast } from '@/hooks/useToast';
import { api } from '@/lib/api';
import { Branding, DEFAULT_BRANDING } from '@/types/whitelabel';

const HEX = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

export default function BrandingPage() {
  const { toast } = useToast();

  const [branding, setBranding] = useState<Branding>(DEFAULT_BRANDING);
  const [domain, setDomain] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.getBranding().then((result) => {
      setBranding(result);
      setLoading(false);
    });
  }, []);

  const set = <K extends keyof Branding>(key: K, value: Branding[K]) =>
    setBranding((prev) => ({ ...prev, [key]: value }));

  const primaryValid = HEX.test(branding.primaryColor);
  const accentValid = HEX.test(branding.accentColor);

  const save = async () => {
    if (!primaryValid || !accentValid) {
      toast({ title: 'Colors must be hex values like #2563EB', variant: 'destructive' });
      return;
    }

    setSaving(true);
    const saved = await api.updateBranding(branding);
    setBranding(saved);
    setSaving(false);
    toast({ title: 'Branding saved', variant: 'success' });
  };

  const saveDomain = async () => {
    const result = await api.setCustomDomain(domain.trim() || null);
    if (result?.success === false) {
      toast({ title: 'Could not save domain', description: result.error, variant: 'destructive' });
      return;
    }
    toast({
      title: domain.trim() ? 'Custom domain saved' : 'Custom domain removed',
      description: domain.trim()
        ? `Point a CNAME for ${domain.trim()} at your Quenaire host to finish setup.`
        : undefined,
      variant: 'success',
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24 text-gray-600">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Branding</h1>
        <p className="text-sm text-gray-500">
          How Quenaire presents itself to your team, your vendors and your Trust Center visitors.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle as="h2" className="flex items-center gap-2">
                <Palette className="h-5 w-5" />
                Identity
              </CardTitle>
              <CardDescription>Name and colors used across the product</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                label="Product name"
                value={branding.productName}
                onChange={(e) => set('productName', e.target.value)}
                placeholder="Acme Trust"
              />

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">Primary color</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="color"
                      value={primaryValid ? branding.primaryColor : '#2563EB'}
                      onChange={(e) => set('primaryColor', e.target.value.toUpperCase())}
                      className="h-10 w-12 cursor-pointer rounded border border-gray-300"
                      aria-label="Pick primary color"
                    />
                    <Input
                      aria-label="Primary colour, hex value"
                      value={branding.primaryColor}
                      onChange={(e) => set('primaryColor', e.target.value)}
                      error={primaryValid ? undefined : 'Must be a hex color'}
                    />
                  </div>
                </div>

                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">Accent color</label>
                  <div className="flex items-center gap-2">
                    <input
                      type="color"
                      value={accentValid ? branding.accentColor : '#7C3AED'}
                      onChange={(e) => set('accentColor', e.target.value.toUpperCase())}
                      className="h-10 w-12 cursor-pointer rounded border border-gray-300"
                      aria-label="Pick accent color"
                    />
                    <Input
                      aria-label="Accent colour, hex value"
                      value={branding.accentColor}
                      onChange={(e) => set('accentColor', e.target.value)}
                      error={accentValid ? undefined : 'Must be a hex color'}
                    />
                  </div>
                </div>
              </div>

              <Input
                label="Logo URL"
                value={branding.logoUrl ?? ''}
                onChange={(e) => set('logoUrl', e.target.value)}
                placeholder="https://cdn.acme.com/logo.svg"
                helperText="Must be an https URL. Other schemes are rejected."
              />

              <Input
                label="Favicon URL"
                value={branding.faviconUrl ?? ''}
                onChange={(e) => set('faviconUrl', e.target.value)}
                placeholder="https://cdn.acme.com/favicon.png"
              />

              <Input
                label="Login background URL"
                value={branding.loginBackgroundUrl ?? ''}
                onChange={(e) => set('loginBackgroundUrl', e.target.value)}
                placeholder="https://cdn.acme.com/login-bg.jpg"
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle as="h2">Documents &amp; support</CardTitle>
              <CardDescription>Applied to PDF exports and error states</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                label="Support email"
                type="email"
                value={branding.supportEmail ?? ''}
                onChange={(e) => set('supportEmail', e.target.value)}
                placeholder="security@acme.com"
              />

              <Input
                label="Document footer"
                value={branding.documentFooter ?? ''}
                onChange={(e) => set('documentFooter', e.target.value)}
                placeholder="Confidential — shared under NDA"
                helperText="Stamped on every exported PDF."
              />

              <label className="flex items-start gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={branding.hidePoweredBy}
                  onChange={(e) => set('hidePoweredBy', e.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                />
                <span>
                  Hide &ldquo;Powered by Quenaire&rdquo;
                  <span className="block text-xs text-gray-500">
                    Available on paid plans. Ignored on the free tier.
                  </span>
                </span>
              </label>

              <Textarea
                label="Custom CSS variables"
                value={branding.customCssVars ?? ''}
                onChange={(e) => set('customCssVars', e.target.value)}
                rows={3}
                placeholder="--radius-card: 12px; --font-heading: Inter;"
                helperText="Only --name: value; declarations are kept — anything else is stripped server-side."
              />
            </CardContent>
          </Card>

          <div className="flex justify-end">
            <Button onClick={save} disabled={saving}>
              {saving ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Save className="mr-1.5 h-4 w-4" />}
              Save branding
            </Button>
          </div>

          <Card>
            <CardHeader>
              <CardTitle as="h2" className="flex items-center gap-2">
                <Globe className="h-5 w-5" />
                Custom domain
              </CardTitle>
              <CardDescription>
                Serve the dashboard and Trust Center from your own hostname
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <Input
                label="Domain"
                value={domain}
                onChange={(e) => setDomain(e.target.value)}
                placeholder="trust.acme.com"
                helperText="Point a CNAME at your Quenaire host, then save. One workspace per domain."
              />
              <Button variant="outline" onClick={saveDomain}>
                Save domain
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* Live preview */}
        <div>
          <Card className="sticky top-6">
            <CardHeader>
              <CardTitle as="h2" className="flex items-center gap-2">
                <Eye className="h-5 w-5" />
                Preview
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-hidden rounded-lg border border-gray-200">
                <div
                  className="flex items-center gap-2 px-4 py-3"
                  style={{ backgroundColor: primaryValid ? branding.primaryColor : '#2563EB' }}
                >
                  {branding.logoUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={branding.logoUrl}
                      alt=""
                      className="h-6 w-6 rounded object-contain"
                      onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
                    />
                  ) : (
                    <span className="flex h-6 w-6 items-center justify-center rounded bg-white/20 text-xs font-bold text-white">
                      {branding.productName.charAt(0).toUpperCase()}
                    </span>
                  )}
                  <span className="text-sm font-semibold text-white">{branding.productName}</span>
                </div>

                <div className="space-y-3 bg-white p-4">
                  <div className="h-2 w-3/4 rounded bg-gray-200" />
                  <div className="h-2 w-1/2 rounded bg-gray-200" />
                  <button
                    type="button"
                    className="rounded-lg px-3 py-1.5 text-xs font-medium text-white"
                    style={{ backgroundColor: primaryValid ? branding.primaryColor : '#2563EB' }}
                  >
                    Primary action
                  </button>
                  <button
                    type="button"
                    className="ml-2 rounded-lg px-3 py-1.5 text-xs font-medium text-white"
                    style={{ backgroundColor: accentValid ? branding.accentColor : '#7C3AED' }}
                  >
                    Accent
                  </button>
                </div>

                {!branding.hidePoweredBy && (
                  <div className="border-t border-gray-100 bg-gray-50 px-4 py-2 text-center text-[10px] text-gray-600">
                    Powered by Quenaire
                  </div>
                )}
              </div>

              <p className="mt-3 text-xs text-gray-500">
                Colors are validated server-side — only hex values are stored, since they are
                interpolated into CSS.
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
