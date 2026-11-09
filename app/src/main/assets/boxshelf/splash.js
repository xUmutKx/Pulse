// Boxshelf start-up: decided before the first paint whether the logo screen shows (Settings → Display → Startup animation)
try { const v = localStorage.getItem('bootIntro'); if (v === 'off') document.documentElement.classList.add('no-splash'); if (v) document.documentElement.dataset.intro = v; } catch (e) {}
