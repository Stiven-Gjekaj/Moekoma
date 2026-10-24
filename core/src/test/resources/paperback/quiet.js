var source = (function (e) {
  // The parts of the Form class of the toolchain that the forms of this extension use.
  class Form { reloadForm() { const id = this.__underlying_formId; if (id) Application.formDidChange(id); } }
  class LanguageForm extends Form {
    constructor() { super(); this.requiresExplicitSubmission = true; this.chosen = Application.getState('languages') ?? ['en']; }
    getSections() {
      return [{ type: 'listSection', id: 'languages', header: 'Languages', items: ['en', 'ja'].map((code) => {
        this['__closure_selector-' + code] = () => { this.chosen = this.chosen.includes(code) ? this.chosen.filter((c) => c !== code) : [...this.chosen, code]; };
        return { type: 'labelRow', id: code, title: code.toUpperCase(), isHidden: false, isSelectable: true,
                 value: this.chosen.includes(code) ? { symbol: 'checkmark', style: 'success' } : undefined, onSelect: Application.Selector(this, '__closure_selector-' + code) };
      }) }];
    }
    async formDidSubmit() { Application.setState(this.chosen, 'languages'); }
  }
  class SettingsForm extends Form {
    getSections() {
      return [
        { type: 'listSection', id: 'main', header: 'Main', footer: 'Changes go at once.', items: [
          { type: 'toggleRow', id: 'adult', title: 'Adult titles', value: Application.getState('adult') ?? false, onValueChange: Application.Selector(this, 'setAdult') },
          { type: 'inputRow', id: 'name', title: 'Name', value: Application.getState('name') ?? '', onValueChange: Application.Selector(this, 'setName') },
          { type: 'navigationRow', id: 'languages', title: 'Languages', value: (Application.getState('languages') ?? ['en']).join(', '), form: new LanguageForm() },
          { type: 'labelRow', id: 'secret', title: 'Secret', isHidden: true },
          { type: 'oauthButtonRow', id: 'login', title: 'Sign in', authorizeEndpoint: 'https://auth.quiet.test/auth', clientId: 'quiet',
            redirectUri: 'quiet://signed-in', scopes: ['read', 'list'], onSuccess: Application.Selector(this, 'signedIn'),
            responseType: { type: 'pkce', tokenEndpoint: 'https://auth.quiet.test/token', pkceCodeLength: 64, pkceCodeMethod: 'S256', formEncodeGrant: true } },
        ] },
        { type: 'listSection', id: 'reset', items: [{ type: 'buttonRow', id: 'reset', title: 'Reset', onSelect: Application.Selector(this, 'reset') }] },
      ];
    }
    async setAdult(value) { Application.setState(value, 'adult'); }
    async setName(value) { Application.setState(value, 'name'); }
    async signedIn(refresh, access) { Application.setState(refresh + '|' + access, 'tokens'); }
    async reset() { Application.resetAllState(); this.reloadForm(); }
  }
  class Quiet {
    constructor() { this.visits = Application.getState('visits') ?? 0; }
    async initialise() {
      Application.registerInterceptor('referer', Application.Selector(this, 'interceptRequest'), Application.Selector(this, 'interceptResponse'));
      Application.registerInterceptor('second', Application.Selector(this, 'addSecond'), Application.Selector(this, 'passResponse'));
    }
    async interceptRequest(request) { return { ...request, headers: { ...(request.headers ?? {}), referer: 'https://quiet.test/' } }; }
    async addSecond(request) { request.headers['x-second'] = request.headers.referer ? 'after-referer' : 'alone'; return request; }
    async interceptResponse(request, response, data) {
      if (response.cookies.length) Application.setState(response.cookies, 'cookies');
      return data;
    }
    async passResponse(request, response, data) { return data; }
    async getDiscoverSections() { return [{ id: 'popular', title: 'Popular', type: 1 }, { id: 'latest_updates', title: 'Latest', type: 3 }]; }
    async getDiscoverSectionItems(section, metadata) {
      const page = metadata ?? 1;
      const [, data] = await Application.scheduleRequest({ url: 'https://quiet.test/' + section.id + '?page=' + page, method: 'GET' });
      const list = JSON.parse(Application.arrayBufferToUTF8String(data));
      return { items: list.map((t) => ({ type: 'simpleCarouselItem', mangaId: t.id, title: t.title, imageUrl: 'https://quiet.test/c/' + t.id + '.jpg' })), metadata: list.length ? page + 1 : undefined };
    }
    async getSettingsForm() { return new SettingsForm(); }
    async getSearchFilters() {
      return [{ type: 'dropdown', id: 'sort', title: 'Sort', options: [{ id: 'new', value: 'Newest' }, { id: 'top', value: 'Top' }], value: 'new' },
              { type: 'multiselect', id: 'genre', title: 'Genre', options: [{ id: 'calm', value: 'Calm' }, { id: 'loud', value: 'Loud' }], value: {}, allowExclusion: true }];
    }
    async getSearchResults(query, metadata) {
      if (query.title === 'web') {
        // The page sets its value some time after the load, as the pages of MangaFire and AllManga do.
        const html = '<html><head><script>setTimeout(function () { window.__answer = "page-" + document.cookie; }, 200);</script></head><body></body></html>';
        const run = await Application.executeInWebView({ source: { html, baseUrl: 'https://quiet.test/read', loadCSS: false, loadImages: false },
          inject: 'return window.__answer', storage: { cookies: [{ name: 'seen', value: 'yes', domain: 'quiet.test', path: '/' }] } });
        const names = run.storage.cookies.map((c) => c.name).join(',');
        return { items: [{ mangaId: 'w', title: String(run.result) + ' with ' + names, imageUrl: '' }], metadata: undefined };
      }
      const filters = (query.filters ?? []).map((f) => f.id + '=' + (typeof f.value === 'string' ? f.value : Object.keys(f.value).sort().map((k) => k + ':' + f.value[k]).join(','))).sort().join(';');
      return { items: [{ mangaId: 'm1', title: 'Found ' + query.title + (filters ? ' with ' + filters : ''), imageUrl: '' }], metadata: undefined };
    }
    async getMangaDetails(mangaId) {
      return { mangaId, mangaInfo: { primaryTitle: 'Morning Tea', thumbnailUrl: 'https://quiet.test/c/m1.jpg', synopsis: '<p>Warm.</p>', secondaryTitles: [],
        contentRating: 'SAFE', author: 'Mina', status: 'Ongoing', tagGroups: [{ id: 'g', title: 'Genres', tags: [{ id: 'a', title: 'Calm' }] }] } };
    }
    async getChapters(manga) {
      return [2, 1].map((n) => ({ chapterId: 'c' + n, sourceManga: manga, langCode: 'en', chapNum: n, title: n === 1 ? 'Kettle' : 'Chapter 2', publishDate: new Date(Date.UTC(2026, 9, n)) }));
    }
    async getChapterDetails(chapter) {
      if (!(chapter.publishDate instanceof Date)) throw new Error('The chapter lost its date.');
      const key = await crypto.subtle.importKey('raw', new TextEncoder().encode('0123456789abcdef'), { name: 'AES-CBC' }, false, ['decrypt']);
      const secret = Application.base64Decode(Application.getState('secret'));
      const plain = await crypto.subtle.decrypt({ name: 'AES-CBC', iv: new TextEncoder().encode('fedcba9876543210') }, key, secret);
      const path = new TextDecoder().decode(plain);
      return { id: chapter.chapterId, mangaId: chapter.sourceManga.mangaId, pages: ['https://quiet.test/' + path + '/1.jpg', 'https://quiet.test/' + path + '/2.jpg#marker'] };
    }
  }
  return e.Quiet = new Quiet(), e.QuietExtension = Quiet, e;
})({});
