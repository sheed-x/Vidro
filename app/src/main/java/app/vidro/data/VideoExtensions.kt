package app.vidro.data

/**
 * File extensions treated as video when scanning storage. Matching by extension (not only MediaStore's
 * media type) is what lets the library show formats Android itself doesn't recognise.
 * Network playlists (m3u, m3u8, sdp) are deliberately left out: Vidro is offline-only.
 */
object VideoExtensions {
    val all: Set<String> = setOf(
        "264", "265", "3g2", "3gp", "3gp2", "3gpp", "3gpp2", "3p2", "60d", "787",
        "ajp", "amv", "arf", "asf", "avi", "bik", "bix", "bnp", "box", "bs4", "bsf", "byu",
        "camrec", "cmrec", "d3v", "dav", "dce", "ddat", "dir", "divx", "dlx",
        "dmb", "dpg", "dsy", "dv", "dv4", "dvr", "dvr-ms", "dvx", "dxr", "evo",
        "eye", "f4p", "f4v", "fbr", "fbz", "flv", "gfp", "gts", "gvi", "h263", "h264",
        "h265", "hdmov", "hevc", "hkm", "ismv", "iva", "ivf", "ivr", "ivs", "jts", "jtv",
        "k3g", "lrec", "lsf", "m15", "m1pg", "m1v", "m21", "m2t", "m2ts", "m2v",
        "m4e", "m4v", "m75", "mgv", "mj2", "mjp", "mjpg",
        "mkv", "mmv", "mnv", "moi", "moov", "mov", "movie", "mp21", "mp2v",
        "mp4", "mp4v", "mpc", "mpe", "mpeg", "mpeg4", "mpg", "mpg2",
        "mpv", "mpv2", "mqv", "mts", "mtv", "mvd", "mve", "mxf", "mys", "nsv",
        "nuv", "ogg", "ogm", "ogv", "ogx", "pgi", "pmf", "pns", "pva", "pvr", "pxv",
        "qt", "qtm", "r3d", "rdb", "rec", "rm", "rmd", "rms", "rmvb", "roq", "rp",
        "rts", "rv", "scm", "scn", "sec", "sfd", "sfvidcap", "skm", "smk",
        "smv", "str", "svi", "tivo", "tod", "tp", "tp0", "tpd", "trp", "ts", "vc1", "vcr",
        "vcv", "vdat", "vdo", "vfw", "vgz", "vid", "viv", "vivo", "vob", "vp3", "vp6",
        "vp7", "vro", "vs4", "vse", "webm", "wm", "wmv", "wtv", "xvid", "y4m",
        "yuv", "zmv",
    )

    fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in all
}
