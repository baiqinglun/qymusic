/* QYMusic 内置元数据插件：LRCLIB（https://lrclib.net） */

var UA = 'QYMusic/0.1.4 (https://github.com/baiqinglun/QYMusic)';

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __httpJson(url) {
  return JSON.parse(Platform.http.getText(url, { headers: { 'User-Agent': UA } }));
}

function __score(item, title, artist, durationMs) {
  var score = 0;
  var itemTitle = String(item.trackName || '').toLowerCase();
  var itemArtist = String(item.artistName || '').toLowerCase();
  var lowerTitle = String(title || '').toLowerCase();
  var lowerArtist = String(artist || '').toLowerCase();
  if (itemTitle && lowerTitle.indexOf(itemTitle) >= 0) score += 2;
  if (itemTitle && itemTitle.indexOf(lowerTitle) >= 0) score += 2;
  if (itemArtist && (lowerArtist.indexOf(itemArtist) >= 0 || itemArtist.indexOf(lowerArtist) >= 0)) score += 2;
  var itemDuration = (item.duration || 0) * 1000;
  if (durationMs > 0 && itemDuration > 0) {
    var diff = Math.abs(itemDuration - durationMs);
    if (diff <= 3000) score += 3;
    else if (diff <= 8000) score += 1;
    else if (diff > 20000) score -= 2;
  }
  if (item.syncedLyrics) score += 1;
  return score;
}

function getLyrics(request) {
  var song = request.song || {};
  var title = song.title || '';
  if (!title) return [];
  var artist = song.artist || '';
  var durationSec = Math.round((song.duration || 0) / 1000);

  var candidates = [];
  try {
    var exact = __httpJson(
      'https://lrclib.net/api/get?track_name=' + __esc(title) +
      '&artist_name=' + __esc(artist) +
      '&album_name=' + __esc(song.album || '') +
      '&duration=' + Math.max(0, durationSec)
    );
    if (exact && (exact.syncedLyrics || exact.plainLyrics)) candidates.push(exact);
  } catch (error) {
    /* /api/get 未命中时返回 404，继续走 /api/search */
  }
  if (!candidates.length) {
    var search = __httpJson(
      'https://lrclib.net/api/search?track_name=' + __esc(title) +
      '&artist_name=' + __esc(artist)
    );
    candidates = search || [];
  }
  if (!candidates.length) return [];

  var best = null;
  var bestScore = -1;
  for (var i = 0; i < candidates.length; i++) {
    var score = __score(candidates[i], title, artist, song.duration || 0);
    if (score > bestScore) {
      bestScore = score;
      best = candidates[i];
    }
  }
  if (!best) return [];

  if (best.syncedLyrics) {
    return [{
      type: 'rawPlainLrc',
      tags: { ti: best.trackName || title, ar: best.artistName || artist, al: best.albumName || song.album || '' },
      rawPlainLrc: String(best.syncedLyrics)
    }];
  }
  if (best.plainLyrics) {
    return [{
      type: 'rawPlainLrc',
      tags: { ti: best.trackName || title, ar: best.artistName || artist, al: best.albumName || song.album || '' },
      rawPlainLrc: String(best.plainLyrics)
    }];
  }
  return [];
}
