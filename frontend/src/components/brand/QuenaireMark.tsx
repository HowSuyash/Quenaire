/**
 * The Quenaire mark: a shield whose contents are a questionnaire, answered.
 *
 * Drawn rather than shipped as a raster so it stays crisp at a favicon's size and a
 * presentation's, weighs a few hundred bytes, and takes its colours from the surrounding
 * theme instead of being baked in at one palette.
 */
export function QuenaireMark({
  className,
  title = 'Quenaire',
}: {
  className?: string;
  title?: string;
}) {
  return (
    <svg
      viewBox="0 0 48 48"
      className={className}
      role="img"
      aria-label={title}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* The shield, open at the upper right where the check leaves it. Bold and
          simple on purpose: this has to survive being a 16px favicon. */}
      <path
        d="M24 4.5 5.5 10.4v12.2C5.5 31.6 12.9 39.4 24 43.2c3.1-1.1 5.9-2.5 8.3-4.3"
        stroke="var(--quenaire-shield, #17385C)"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Two lines of a form, the second shorter: a questionnaire being worked through. */}
      <g fill="var(--quenaire-accent, #2680F5)">
        <rect x="12" y="14.5" width="16.5" height="4.2" rx="2.1" />
        <rect x="12" y="22" width="10.5" height="4.2" rx="2.1" />
      </g>

      {/* The answer, sweeping out past the shield's open edge. */}
      <path
        d="M15.5 30.5 21.5 36.5 38 14"
        stroke="var(--quenaire-accent, #2680F5)"
        strokeWidth="4.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Under the shield's point, so the silhouette reads as a question mark as well
          as a shield. Inside the square box, so any icon slot fits it. */}
      <circle cx="24" cy="46" r="2.6" fill="var(--quenaire-shield, #17385C)" />
    </svg>
  );
}

/**
 * Mark plus name, for headers and sign-in.
 *
 * The name is set in text rather than outlined so it stays selectable, searchable and
 * legible to a screen reader.
 */
export function QuenaireLogo({ className }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className ?? ''}`}>
      <QuenaireMark className="h-8 w-8 shrink-0" />
      <span className="text-xl font-semibold tracking-tight text-[color:var(--quenaire-shield,#17385C)]">
        Quenaire
      </span>
    </span>
  );
}
