/* QYMusic 内置元数据插件：酷我音乐 */

var UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __httpJson(url, referer) {
  return JSON.parse(Platform.http.getText(url, {
    headers: { 'User-Agent': UA, 'Referer': referer || 'https://www.kuwo.cn/' }
  }));
}

function __clean(value) {
  return String(value == null ? '' : value)
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function __absoluteCover(value) {
  var cover = __clean(value);
  if (!cover) return '';
  if (cover.indexOf('http') === 0) return cover;
  /* web_albumpic_short 可能是相对路径（如 120/s3s94/93/xxx.jpg），需拼 img4 域名 */
  if (/^\d+x?\d*\//.test(cover) || /^\d+\//.test(cover)) {
    return 'http://img4.kuwo.cn/star/albumcover/' + cover;
  }
  return 'http://' + cover;
}

function __search(keyword, page, pageSize) {
  var data = __httpJson(
    'https://search.kuwo.cn/r.s?all=' + __esc(keyword) +
    '&ft=music&itemset=web_2013&client=kt&pn=' + (Math.max(1, page || 1) - 1) +
    '&rn=' + (pageSize || 20) + '&rformat=json&encoding=utf8&vipver=1'
  );
  var list = (data && data.abslist) || [];
  return list.map(function (item) {
    var rid = String(item.MUSICRID || item.DC_TARGETID || '').replace(/^[^_]*_/, '');
    var cover = __absoluteCover(item.web_albumpic_short || item.albumpic || '');
    return {
      id: rid,
      title: __clean(item.NAME),
      artist: __clean(item.ARTIST),
      album: __clean(item.ALBUM),
      duration: (parseInt(item.DURATION, 10) || 0) * 1000,
      picUrl: cover
    };
  }).filter(function (item) { return !!item.id; });
}

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

function __fmt(seconds) {
  var total = Math.max(0, Math.round(parseFloat(seconds) * 100));
  var centis = total % 100;
  var sec = Math.floor(total / 100) % 60;
  var minutes = Math.floor(total / 6000);
  var pad = function (value) { return (value < 10 ? '0' : '') + value; };
  return pad(minutes) + ':' + pad(sec) + '.' + pad(centis);
}

function __pickSong(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  var candidates = __search(keyword || song.title || '', 1, 10);
  return __bestMatch(candidates, song.title, song.artist, song.duration || 0);
}

function searchSongs(request) {
  return __search(request.keyword || '', request.page || 1, request.pageSize || 20);
}

function getLyrics(request) {
  var song = request.song || {};
  var match = __pickSong(request);
  if (!match) return [];
  var data = __httpJson(
    'https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=' + __esc(match.id),
    'https://m.kuwo.cn/'
  );
  var lines = (data && data.data && data.data.lrclist) || [];
  if (!lines.length) return [];
  var out = [];
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i];
    var text = __clean(line.lineLyric);
    if (!text) continue;
    out.push('[' + __fmt(line.time) + ']' + text);
  }
  if (!out.length) return [];
  return [{
    type: 'rawPlainLrc',
    tags: { ti: match.title || song.title || '', ar: match.artist || song.artist || '', al: match.album || song.album || '' },
    rawPlainLrc: out.join('\n')
  }];
}

function searchCovers(request) {
  var song = request.song || {};
  var direct = song.internal && song.internal.picUrl;
  var match = __pickSong(request);
  var cover = (match && match.picUrl) || direct || '';
  if (!cover) return [];
  var results = [{ url: cover, width: 120 }];
  var upgraded = cover
    .replace('/120x120/', '/500x500/')
    .replace('/120/', '/500/');
  if (upgraded !== cover) results.unshift({ url: upgraded, width: 500 });
  return results;
}
