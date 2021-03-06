package com.hx.android.ehentai.net;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import android.content.Context;

import com.hx.android.ehentai.model.Comic;
import com.hx.android.ehentai.util.FileManager;
import com.hx.android.ehentai.util.NetWorkHelper;

public class WebManager {

	private Context mContext;
	private String mContent;
	private int mPage;
	private boolean cachePage;
	private String cacheDir;
	private File cacheIndexFile;
	private String imageUrlCacheDir;

	public enum ImageQuality {
		X420, X780, X980
	}

	public final static String E_HENTAI_LOFI_URL = "http://lofi.e-hentai.org/";
	public final static String E_HENTAI_LOFI_CHINESE_URL = "http://lofi.e-hentai.org/?%s&f_search=chinese&f_sname=1&f_stags=1&f_apply=Apply+Filter";
	public final static String E_HENTAI_LOFI_CHINESE_URL1 = "http://lofi.e-hentai.org/?%s&f_search=%%E6%%BC%%A2%%E5%%8C%%96&f_sname=1&f_stags=1&f_apply=Apply+Filter";
	public final static String E_HENTAI_LOFI_CHINESE_URL2 = "http://lofi.e-hentai.org/?%s&f_search=%%E6%%B1%%89%%E5%%8C%%96&f_sname=1&f_stags=1&f_apply=Apply+Filter";
	//public final static String E_HENTAI_LOFI_CHINESE_URL = "http://lofi.e-hentai.org/?%s&f_search=%%E6%%BC%%A2%%E5%%8C%%96+chinese+%%E6%%B1%%89%%E5%%8C%%96&f_sname=1&f_stags=1&f_apply=Apply+Filter";
	public final static Pattern LIST_PATTERN = Pattern
			.compile("(?<=<div class=\"ig\">)[\\S\\s]*?(?=</div>)");
	public final static Pattern COVER_PATTERN = Pattern
			.compile("(?<=<img src=\")[\\S\\s]*?(?=\" alt=\"Cover Image\" />)");
	public final static Pattern LINK_PATTERN = Pattern
			.compile("(?<=class=\"fp\"><a href=\")[\\S\\s]*?(?=\">Go To First Page</a>)");
	public final static Pattern TITLE_PATTERN = Pattern
			.compile("(?<=<a class=\"b\" href=\")[\\S\\s]*?(?=</a>)");
	public final static Pattern TITLE_PATTERN1 = Pattern
			.compile("(?<=\">)[\\S\\s]*");
	public final static Pattern RATING_PATTERN = Pattern
			.compile("(?<=Rating:</td><td class=\"ir\">)[\\S\\s]*?(?=</td>)");
	public final static Pattern PAGE_IMAGE_PATTERN = Pattern
			.compile("(?<=<img id=\"sm\" src=\")[\\S\\s]*(?=\" alt=)");
	public final static Pattern NEXT_PAGE_PATTERN = Pattern
			.compile("(?<=<div id=\"sd\">\n<a href=\")[\\S\\s]*(?=\"><img id=\"sm\")");

	public WebManager(Context context) {
		mContext = context;
		mPage = 0;
		cachePage = false;
		cacheDir = context.getCacheDir().getAbsolutePath();
		imageUrlCacheDir = String.format("%s/ImageUrlCache/", context
				.getFilesDir().getAbsolutePath());
		try {
			cacheIndexFile = new File(String.format("%scacheIdx", context
					.getCacheDir().getAbsolutePath()));
			if (!cacheIndexFile.exists())
				cacheIndexFile.createNewFile();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setCachePageOn(boolean open) {
		cachePage = open;
	}

	public void setImageQuality(ImageQuality imageQuality) {
		String param = null;

		switch (imageQuality) {
		case X780:
			param = "setres=2";
			break;
		case X980:
			param = "setres=3";
			break;
		default:
			param = "setres=1";
		}

		try {
			NetWorkHelper.httpClientRequest(
					String.format("%s?%s", E_HENTAI_LOFI_URL, param),
					NetWorkHelper.HttpRequestMethod.GET, null, true);
			String cookies = NetWorkHelper.getCookies();
			if (cookies == null || cookies.equals("")) {
				NetWorkHelper.httpClientRequest(
						String.format("%s?%s", E_HENTAI_LOFI_URL, param),
						NetWorkHelper.HttpRequestMethod.GET, null, true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void getAllImageUrlAndCache(String startPage,
			List<String> imageUrlList) {
		File cache = new File(imageUrlCacheDir + startPage.hashCode());
		if (cache.exists()) {
			String content = FileManager.readAllText(cache, "utf-8").trim();
			if (content == null || "".equals(content))
				return;
			String[] urlList = content.substring(1, content.length() - 1)
					.split(", ");
			if (urlList.length > 0) {
				imageUrlList.addAll(Arrays.asList(urlList));
				return;
			}
		}

		this.getAllImageUrl(startPage, imageUrlList);

		FileManager.writeAllText(cache,
				Arrays.toString(imageUrlList.toArray()), "utf-8");
	}

	public void getAllImageUrl(String startPage, List<String> imageUrlList) {
		if (imageUrlList == null)
			return;

		try {
			int retryTimes = 0;
			String content = NetWorkHelper.httpClientRequest(startPage,
					NetWorkHelper.HttpRequestMethod.GET, null, true);

			while (content.equals("") && retryTimes < 3) {
				Thread.sleep(100);
				content = NetWorkHelper.httpClientRequest(startPage,
						NetWorkHelper.HttpRequestMethod.GET, null, true);
				retryTimes++;
			}

			Matcher imageMatcher = PAGE_IMAGE_PATTERN.matcher(content);
			Matcher pageMatcher = NEXT_PAGE_PATTERN.matcher(content);

			if (imageMatcher.find())
				imageUrlList.add(imageMatcher.group());
			if (pageMatcher.find()) {
				String url = pageMatcher.group();
				if (url.equals(startPage))
					return;
				getAllImageUrl(url, imageUrlList);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<Comic> loadMoreComic() {
		List<Comic> ret = new ArrayList<Comic>();

		try {

			int retryTimes = 0;

			mContent = NetWorkHelper.httpClientRequest(
					String.format(E_HENTAI_LOFI_CHINESE_URL,
							mPage > 0 ? String.format("page=%d", mPage) : ""),
					NetWorkHelper.HttpRequestMethod.GET, null, true);

			while (mContent.equals("") && retryTimes < 3) {
				Thread.sleep(100);
				mContent = NetWorkHelper.httpClientRequest(String.format(
						E_HENTAI_LOFI_CHINESE_URL,
						mPage > 0 ? String.format("page=%d", mPage) : ""),
						NetWorkHelper.HttpRequestMethod.GET, null, true);
			}

			Matcher matcher = LIST_PATTERN.matcher(mContent);

			while (matcher.find()) {
				String content = matcher.group();

				Comic comic = new Comic();

				Matcher coverMatcher = COVER_PATTERN.matcher(content);

				Matcher linkMatcher = LINK_PATTERN.matcher(content);

				Matcher titleMatcher = TITLE_PATTERN.matcher(content);

				Matcher ratingMatcher = RATING_PATTERN.matcher(content);

				if (coverMatcher.find()) {
					comic.coverPath = coverMatcher.group();
				}

				if (linkMatcher.find())
					comic.firstPageUrl = linkMatcher.group();

				if (titleMatcher.find()) {
					Matcher titleMatcher1 = TITLE_PATTERN1.matcher(titleMatcher
							.group());
					if (titleMatcher1.find())
						comic.title = titleMatcher1.group();
				}

				if (ratingMatcher.find())
					comic.rating = ratingMatcher.group().trim().length();

				ret.add(comic);
			}

			mPage++;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	public void setPage(int page) {
		mPage = page;
	}

	public void cacheComic(String link, String savePath) {

	}

	/*
	 * cahce index format: HashCode,start,end,CahceFileName\n
	 */
	private String getCache(String url) {
		String ret = null;
		String urlHashCode = String.valueOf(url.hashCode());
		String indexStr = FileManager.readAllText(cacheIndexFile, "utf-8");
		int start = indexStr.indexOf(urlHashCode);
		if (start > 0) {
			indexStr = indexStr.substring(start, indexStr.length());
			int end = indexStr.indexOf("\n");
			if (end > 0) {
				String line = indexStr.substring(0, end);
				if (line != null && !"".equals(line)) {
					String[] arr = line.split(",");
					if (arr.length == 4)
						ret = FileManager.readAllText(arr[3], "utf-8")
								.substring(Integer.valueOf(arr[1]),
										Integer.valueOf(arr[2]));
				}
			}
		}

		return ret;
	}
}
