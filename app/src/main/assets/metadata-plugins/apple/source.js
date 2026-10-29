/* QYMusic 内置元数据插件：Apple Music
 *
 * - 搜索与封面走 iTunes Search API（免鉴权）；
 * - 逐字歌词走 amp-api（MusicKit），developer token 从音乐网页版
 *   的 JS 资源中提取并缓存（约 20 小时，与 Lyrico / LDDC 的做法一致）。
 */

var UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
var TOKEN_CACHE_KEY = 'apple.developer_token';
var TOKEN_TTL_MS = 20 * 60 * 60 * 1000;
var STOREFRONT = 'us';

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __bestMatch(candidates, title, artist, durationMs) {
  var best = null;
  var bestScore = -1;
  var lowerTitle = String(title || '').toLowerCase();
  var lowerArtist = String(artist || '').toLowerCase();
  for (var i = 0; i < candidates.length; i++) {
    var item = candidates[i];
    var itemTitle = String(item.title || '').toLowerCase();
    var itemArtist = String(item.artist || '').toLowerCase();
    var score = 0;
    if (itemTitle && lowerTitle.indexOf(itemTitle) >= 0) score += 2;
    if (itemTitle && itemTitle.indexOf(lowerTitle) >= 0) score += 2;
    if (itemArtist && (lowerArtist.indexOf(itemArtist) >= 0 || itemArtist.indexOf(lowerArtist) >= 0)) score += 2;
    if (i === 0) score += 1;
    var itemDuration = item.duration || 0;
    if (durationMs > 0 && itemDuration > 0) {
      var diff = Math.abs(itemDuration - durationMs);
      if (diff <= 3000) score += 3;
      else if (diff <= 8000) score += 1;
      else if (diff > 20000) score -= 2;
    }
    if (score > bestScore) {
      bestScore = score;
      best = item;
    }
  }
  return best;
}

function __itunesSearch(keyword, limit) {
  var body = Platform.http.getText(
    'https://itunes.apple.com/search?term=' + __esc(keyword) +
    '&media=music&entity=song&limit=' + (limit || 20) + '&country=' + STOREFRONT,
    { headers: { 'User-Agent': UA } }
  );
  var data = JSON.parse(body);
  var results = (data && data.results) || [];
  return results.map(function (item) {
    return {
      id: String(item.trackId || ''),
      title: item.trackName || '',
      artist: item.artistName || '',
      album: item.collectionName || '',
      duration: item.trackTimeMillis || 0,
      date: String(item.releaseDate || '').slice(0, 10),
      trackNumber: item.trackNumber ? String(item.trackNumber) : '',
      picUrl: item.artworkUrl100
        ? String(item.artworkUrl100).replace('100x100bb', '2048x2048bb')
        : ''
    };
  });
}

function searchSongs(request) {
  return __itunesSearch(request.keyword || '', request.pageSize || 20);
}

function searchCovers(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  var matches = __itunesSearch(keyword || song.title || '', 5);
  var match = __bestMatch(matches, song.title, song.artist, song.duration || 0);
  if (!match || !match.picUrl) return [];
  return [{ url: match.picUrl, width: 2048, height: 2048 }];
}

/* ---- developer token ---- */

function __getToken() {
  var cached = Platform.cache.get(TOKEN_CACHE_KEY);
  if (cached) return cached;
  var html = Platform.http.getText('https://music.apple.com/' + STOREFRONT + '/browse', {
    headers: { 'User-Agent': UA }
  });
  var seen = {};
  var scripts = html.match(/\/assets\/[A-Za-z0-9._-]+\.js/g) || [];
  for (var i = 0; i < scripts.length && i < 4; i++) {
    var path = scripts[i];
    if (seen[path]) continue;
    seen[path] = true;
    if (!/index|main/.test(path)) continue;
    var js;
    try {
      js = Platform.http.getText('https://music.apple.com' + path, {
        headers: { 'User-Agent': UA }
      });
    } catch (error) {
      continue;
    }
    var match = js.match(/(eyJhbGciO[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/);
    if (match) {
      Platform.cache.set(TOKEN_CACHE_KEY, match[1], TOKEN_TTL_MS);
      return match[1];
    }
  }
  throw new Error('无法从 Apple Music 提取 token');
}

function __ampApi(path) {
  var token = __getToken();
  var body = Platform.http.getText('https://amp-api.music.apple.com' + path, {
    headers: {
      'User-Agent': UA,
      'Authorization': 'Bearer ' + token,
      'Origin': 'https://music.apple.com',
      'Referer': 'https://music.apple.com/'
    }
  });
  return JSON.parse(body);
}

function __findTtml(payload) {
  if (!payload || !payload.data || !payload.data.length) return '';
  var song = payload.data[0];
  var lyrics = song.relationships && song.relationships.lyrics;
  if (lyrics && lyrics.data && lyrics.data.length) {
    return (lyrics.data[0].attributes && lyrics.data[0].attributes.ttml) || '';
  }
  return (song.attributes && song.attributes.ttml) || '';
}

/* TTML 时间格式：[h:]m:s.mmm 或 s.mmm(s) */
function __parseTime(value) {
  var text = String(value || '').replace(/s$/, '');
  var parts = text.split(':');
  var seconds = 0;
  for (var i = 0; i < parts.length; i++) {
    seconds = seconds * 60 + parseFloat(parts[i] || '0');
  }
  return Math.round(seconds * 1000);
}

function __pad(value) {
  var number = parseInt(value, 10);
  return (number < 10 ? '0' : '') + number;
}

function __fmt(ms) {
  var total = Math.max(0, Math.round(ms / 10));
  var centis = total % 100;
  var seconds = Math.floor(total / 100) % 60;
  var minutes = Math.floor(total / 6000);
  return __pad(minutes) + ':' + __pad(seconds) + '.' + __pad(centis);
}

function __stripTags(fragment) {
  return String(fragment || '')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .trim();
}

function __ttmlToLrc(ttml) {
  var paragraphs = Platform.xml.findElements(ttml, { tag: 'p' });
  if (!paragraphs.length) return '';
  var out = [];
  paragraphs.forEach(function (p) {
    var start = __parseTime(p.attrs.begin);
    var end = __parseTime(p.attrs.end);
    var spans = [];
    try {
      spans = Platform.xml.findElements(p.innerXml, { tag: 'span' });
    } catch (error) {
      spans = [];
    }
    if (spans.length) {
      var body = spans.map(function (span) {
        var wordStart = __parseTime(span.attrs.begin) || start;
        var wordEnd = __parseTime(span.attrs.end) || end || wordStart;
        var text = String(span.text || '').replace(/\s+$/, '');
        return '<' + __fmt(wordStart) + '>' + text;
      }).join(' ');
      out.push('[' + __fmt(start) + ']' + body);
    } else {
      out.push('[' + __fmt(start) + ']' + __stripTags(p.innerXml || p.text));
    }
  });
  return out.filter(Boolean).join('\n');
}

function getLyrics(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  if (!keyword) return [];
  var matches = __itunesSearch(keyword, 5);
  var match = __bestMatch(matches, song.title, song.artist, song.duration || 0);
  if (!match || !match.id) return [];
  var payload = __ampApi(
    '/v1/catalog/' + STOREFRONT + '/songs/' + __esc(match.id) +
    '?include=lyrics&extend=lyrics&l=' + STOREFRONT
  );
  var ttml = __findTtml(payload);
  if (!ttml) return [];
  var enhanced = __ttmlToLrc(ttml);
  if (!enhanced) return [];
  return [{
    type: 'rawEnhancedLrc',
    tags: {
      ti: match.title || song.title || '',
      ar: match.artist || song.artist || '',
      al: match.album || song.album || '',
      date: match.date || ''
    },
    rawEnhancedLrc: enhanced
  }];
}
