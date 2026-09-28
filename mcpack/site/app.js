/* Немного поведения: появление блоков, подсветка карты, копирование команд. */
(() => {
  'use strict';

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* --- шапка: тень при прокрутке и активный раздел --- */
  const nav = document.querySelector('.nav');
  const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 24);
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  const links = [...document.querySelectorAll('.nav-links a')];
  const sections = links
    .map((a) => document.querySelector(a.getAttribute('href')))
    .filter(Boolean);

  if ('IntersectionObserver' in window && sections.length) {
    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          links.forEach((a) =>
            a.classList.toggle('active', a.getAttribute('href') === '#' + entry.target.id)
          );
        });
      },
      { rootMargin: '-45% 0px -50% 0px' }
    );
    sections.forEach((section) => spy.observe(section));
  }

  /* --- появление блоков при прокрутке --- */
  const reveals = [...document.querySelectorAll('.reveal, .bars')];
  if (reduced || !('IntersectionObserver' in window)) {
    reveals.forEach((el) => el.classList.add('in-view'));
  } else {
    const shower = new IntersectionObserver(
      (entries, observer) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('in-view');
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.15 }
    );
    reveals.forEach((el) => shower.observe(el));
  }

  /* --- карта: связка метки и строки легенды --- */
  const frame = document.querySelector('.map-frame');
  const tip = document.querySelector('.map-tip');
  const pins = [...document.querySelectorAll('.map-svg .pin')];
  const rows = [...document.querySelectorAll('.legend-item')];

  const highlight = (id) => {
    pins.forEach((pin) => {
      const match = pin.dataset.pin === id;
      pin.classList.toggle('hot', match);
      pin.classList.toggle('dim', id !== null && !match);
    });
    rows.forEach((row) => {
      row.style.background = row.dataset.pin === id ? 'rgba(255,255,255,.07)' : '';
    });
  };

  const showTip = (pin) => {
    if (!tip || !frame) return;
    const box = frame.getBoundingClientRect();
    const spot = pin.getBoundingClientRect();
    tip.innerHTML =
      '<b>' + pin.dataset.name + '</b><span>' + pin.dataset.xy + '</span>';
    tip.style.left = spot.left - box.left + spot.width / 2 + 'px';
    tip.style.top = spot.top - box.top + 'px';
    tip.classList.add('show');
  };

  pins.forEach((pin) => {
    const enter = () => {
      highlight(pin.dataset.pin);
      showTip(pin);
    };
    const leave = () => {
      highlight(null);
      tip && tip.classList.remove('show');
    };
    pin.addEventListener('mouseenter', enter);
    pin.addEventListener('mouseleave', leave);
    pin.addEventListener('focus', enter);
    pin.addEventListener('blur', leave);
  });

  rows.forEach((row) => {
    row.addEventListener('mouseenter', () => {
      highlight(row.dataset.pin);
      const pin = pins.find((p) => p.dataset.pin === row.dataset.pin);
      if (pin) showTip(pin);
    });
    row.addEventListener('mouseleave', () => {
      highlight(null);
      tip && tip.classList.remove('show');
    });
  });

  /* --- копирование команд --- */
  document.querySelectorAll('.copy').forEach((button) => {
    button.addEventListener('click', async () => {
      const code = button.parentElement.querySelector('code');
      if (!code) return;
      try {
        await navigator.clipboard.writeText(code.textContent.trim());
      } catch (error) {
        const range = document.createRange();
        range.selectNodeContents(code);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        return;
      }
      const was = button.textContent;
      button.textContent = 'скопировано';
      button.classList.add('done');
      setTimeout(() => {
        button.textContent = was;
        button.classList.remove('done');
      }, 1600);
    });
  });

  /* --- окно выбора лаунчера ---
     Кнопки скачивания сначала спрашивают, официальный лаунчер или пиратский:
     от этого зависит формат файла. Без JS ссылка работает как обычная. */
  const overlay = document.querySelector('.dl-overlay');
  if (overlay) {
    const sheet = overlay.querySelector('.dl-sheet');
    const triggers = [...document.querySelectorAll('[data-download]')];
    let opener = null;

    const focusable = () =>
      [...sheet.querySelectorAll('a[href], button')].filter((el) => !el.hidden);

    const open = (event) => {
      event.preventDefault();
      opener = event.currentTarget;
      overlay.hidden = false;
      document.body.style.overflow = 'hidden';
      // Кадр задержки: пока элемент скрыт, переход не запускается.
      requestAnimationFrame(() => {
        overlay.classList.add('open');
        const first = focusable()[1] || focusable()[0];
        if (first) first.focus({ preventScroll: true });
      });
    };

    const close = () => {
      if (overlay.hidden) return;
      overlay.classList.remove('open');
      document.body.style.overflow = '';
      const done = () => {
        overlay.hidden = true;
        if (opener) opener.focus({ preventScroll: true });
      };
      if (reduced) done();
      else setTimeout(done, 260);
    };

    triggers.forEach((button) => button.addEventListener('click', open));
    overlay.querySelector('.dl-close').addEventListener('click', close);
    overlay.addEventListener('mousedown', (event) => {
      if (event.target === overlay) close();
    });
    // Скачивание началось — окно больше не нужно.
    overlay.querySelectorAll('a[download]').forEach((link) =>
      link.addEventListener('click', () => setTimeout(close, 180))
    );

    document.addEventListener('keydown', (event) => {
      if (overlay.hidden) return;
      if (event.key === 'Escape') {
        close();
        return;
      }
      if (event.key !== 'Tab') return;
      // Фокус не должен уходить из окна, пока оно открыто.
      const items = focusable();
      if (!items.length) return;
      const edge = event.shiftKey ? items[0] : items[items.length - 1];
      if (document.activeElement === edge) {
        event.preventDefault();
        (event.shiftKey ? items[items.length - 1] : items[0]).focus();
      }
    });
  }
})();
