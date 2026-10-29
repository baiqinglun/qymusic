/* QYMusic 内置元数据插件：酷狗音乐（KRC 逐字歌词解码后输出增强 LRC） */

var UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __httpJson(url) {
  return JSON.parse(Platform.http.getText(url, {
    headers: { 'User-Agent': UA, 'Referer': 'https://www.kugou.com/' }
  }));
}

function __joinArtists(list) {
  if (!list || !list.length) return '';
  var names = [];
  for (var i = 0; i < list.length; i++) {
    var item = list[i];
    names.push(typeof item === 'string' ? item : (item && item.name) || '');
  }
  return names.filter(function (name) { return !!name; }).join(' / ');
}

function __search(keyword, page, pageSize) {
  /* 注意：mobilecdn.kugou.com 的 https 不可达，必须用 http */
  var data = __httpJson(
    'http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=' + __esc(keyword) +
    '&page=' + Math.max(1, page || 1) + '&pagesize=' + (pageSize || 20) + '&showtype=1'
  );
  var info = (data && data.data && data.data.info) || [];
  return info.map(function (item) {
    return {
      id: String(item.hash || ''),
      title: item.songname || '',
      artist: item.singername || '',
      album: item.album_name || '',
      duration: (item.duration || 0) * 1000,
      picUrl: '',
      internal: { hash: item.hash || '', albumId: item.album_id || '', audioId: item.audio_id || '' }
    };
  });
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

function __centis(tag) {
  var m = String(tag).match(/\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/);
  if (!m) return -1;
  var frac = m[3] ? parseInt(m[3], 10) : 0;
  if (String(m[3] || '').length >= 3) frac = Math.round(frac / 10);
  return parseInt(m[1], 10) * 6000 + parseInt(m[2], 10) * 100 + frac;
}

function __mergeTranslation(original, translation) {
  var trans = {};
  String(translation || '').split(/\r?\n/).forEach(function (line) {
    var tags = line.match(/^\s*((?:\[[^\]]+\])+)/);
    if (!tags) return;
    var text = line.replace(/^\s*(?:\[[^\]]+\])+/, '').trim();
    if (!text) return;
    tags[1].match(/\[[^\]]+\]/g).forEach(function (tag) {
      var key = __centis(tag);
      if (key < 0) return;
      if (!trans[key]) trans[key] = [];
      trans[key].push(text);
    });
  });
  var out = [];
  String(original || '').split(/\r?\n/).forEach(function (line) {
    out.push(line);
    var tags = line.match(/^\s*((?:\[[^\]]+\])+)/);
    if (!tags) return;
    var body = line.replace(/^\s*(?:\[[^\]]+\])+/, '').trim();
    if (!body) return;
    tags[1].match(/\[[^\]]+\]/g).forEach(function (tag) {
      var key = __centis(tag);
      var list = key >= 0 ? trans[key] : null;
      if (list) list.forEach(function (text) { out.push(tag + text); });
    });
  });
  return out.join('\n');
}

function __krcToLrc(krc) {
  var out = [];
  String(krc).split(/\r?\n/).forEach(function (line) {
    var m = line.match(/^\[(\d+),(\d+)\]([\s\S]*)$/);
    if (!m) {
      out.push(line);
      return;
    }
    var start = parseInt(m[1], 10);
    var end = start + parseInt(m[2], 10);
    var words = m[3].match(/<\d+,\d+,\d+>[^<]*/g) || [];
    var body = words.map(function (raw) {
      var p = raw.match(/^<(\d+),(\d+),\d+>([\s\S]*)$/);
      if (!p) return '';
      var wordStart = start + parseInt(p[1], 10);
      var wordEnd = wordStart + parseInt(p[2], 10);
      return '<' + __fmt(wordStart) + '>' + p[3];
    }).join('');
    out.push('[' + __fmt(start) + ']' + body);
  });
  return out.join('\n');
}

/* KRC 内容经过 XOR（密钥 @Gvs）+ zlib 压缩 */
var KRC_XOR_KEY = [0x40, 0x47, 0x86, 0x76];

function __decodeKrc(base64Content) {
  var bytes = Platform.base64.decodeBytes(base64Content);
  var xored = Platform.bytes.xor(bytes, KRC_XOR_KEY);
  return Platform.compression.inflateBytesToText(xored);
}

function searchSongs(request) {
  return __search(request.keyword || '', request.page || 1, request.pageSize || 20);
}

function getLyrics(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  if (!keyword) return [];
  var internal = song.internal || {};
  var searchUrl = 'https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=' + __esc(keyword);
  var durationMs = song.duration || 0;
  if (durationMs > 0) searchUrl += '&duration=' + durationMs;
  if (internal.hash) searchUrl += '&hash=' + __esc(internal.hash);
  var searchResult = __httpJson(searchUrl);
  var candidates = (searchResult && searchResult.candidates) || [];
  if (!candidates.length) return [];
  candidates.sort(function (a, b) {
    return (a.duration || 0) === (b.duration || 0)
      ? 0
      : (Math.abs((a.duration || 0) - durationMs) - Math.abs((b.duration || 0) - durationMs));
  });
  var candidate = candidates[0];
  if (!candidate || !candidate.id) return [];

  var downloadUrl = 'https://lyrics.kugou.com/download?ver=1&client=pc&fmt=krc&charset=utf8&id=' +
    __esc(candidate.id) + '&accesskey=' + __esc(candidate.accesskey || '');
  var result = __httpJson(downloadUrl);
  if (!result || String(result.status) !== '200' || !result.content) {
    downloadUrl = downloadUrl.replace('fmt=krc', 'fmt=lrc');
    result = __httpJson(downloadUrl);
    if (!result || String(result.status) !== '200' || !result.content) return [];
    var plain = Platform.base64.decodeText(result.content);
    if (!plain) return [];
    return [{
      type: 'rawPlainLrc',
      tags: { ti: candidate.songname || song.title || '', ar: candidate.singername || song.artist || '' },
      rawPlainLrc: plain
    }];
  }
  var krc = __decodeKrc(result.content);
  var enhanced = __krcToLrc(krc);
  if (!enhanced) return [];
  return [{
    type: 'rawEnhancedLrc',
    tags: { ti: candidate.songname || song.title || '', ar: candidate.singername || song.artist || '' },
    rawEnhancedLrc: enhanced
  }];
}

function searchCovers(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  var matches = __search(keyword || song.title || '', 1, 5);
  var albumId = '';
  for (var i = 0; i < matches.length; i++) {
    var item = matches[i];
    var sameTitle = String(item.title || '').toLowerCase() === String(song.title || '').toLowerCase();
    var sameArtist = String(item.artist || '').toLowerCase().indexOf(String(song.artist || '').toLowerCase()) >= 0;
    if (sameTitle && sameArtist && item.internal && item.internal.albumId) {
      albumId = item.internal.albumId;
      break;
    }
    if (!albumId && item.internal && item.internal.albumId) albumId = item.internal.albumId;
  }
  if (!albumId) return [];
  var album = __httpJson('http://mobilecdn.kugou.com/api/v3/album/info?albumid=' + __esc(albumId));
  var data = (album && album.data) || {};
  /* 封面在 imgurl 字段，且带 {size} 占位符（如 stdmusic/{size}/...） */
  var img = data.imgurl || data.img || '';
  if (!img) return [];
  img = img.replace('{size}', '480').replace('http://', 'https://');
  return [{ url: img, width: 480, height: 480 }];
}
