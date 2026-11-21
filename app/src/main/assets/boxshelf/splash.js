// Boxshelf start-up: decided before the first paint whether the logo screen shows (Settings → Display → Startup animation)
try { const v = localStorage.getItem('bootIntro'); if (v === 'off') document.documentElement.classList.add('no-splash'); if (v) document.documentElement.dataset.intro = v; } catch (e) {}
// Pulse: Game Hub opens straight onto the shelf, with no start-up logo (it held the screen for seconds while the covers loaded); the startup animation setting still decides the cases' own entrance
try { if (window.PulseHost) { document.documentElement.classList.add('no-splash'); document.documentElement.dataset.intro = 'off'; } } catch (e) {}
