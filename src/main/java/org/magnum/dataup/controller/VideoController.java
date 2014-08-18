package org.magnum.dataup.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.VideoFileManager;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class VideoController {
	private final Logger logger = Logger.getLogger(VideoController.class
			.getName());

	public static final String DATA_PARAMETER = "data";

	public static final String ID_PARAMETER = "id";

	public static final String VIDEO_SVC_PATH = "/video";

	public static final String VIDEO_DATA_PATH = VIDEO_SVC_PATH + "/{id}/data";

	private List<Video> videos = new CopyOnWriteArrayList<Video>();

	private static final AtomicLong currentId = new AtomicLong(0L);

	@Autowired
	VideoFileManager vdfm;

	@Autowired
	VideoStatus videoStatus;

	@RequestMapping(value = VIDEO_SVC_PATH, method = RequestMethod.GET)
	public @ResponseBody List<Video> getVideoList() {

		return videos;
	}

	@RequestMapping(value = VIDEO_SVC_PATH, method = RequestMethod.POST)
	public @ResponseBody Video addVideo(@RequestBody Video v) {

		if (v.getId() == 0) {
			v.setId(currentId.incrementAndGet());
		}
		if (v.getDataUrl() == null) {
			v.setDataUrl(getDataUrl(v.getId()));
		}
		videos.add(v);
		return v;
	}

	@RequestMapping(value = VIDEO_DATA_PATH, method = RequestMethod.POST)
	public @ResponseBody VideoStatus setVideoData(
			@PathVariable(ID_PARAMETER) long id,
			@RequestParam(DATA_PARAMETER) MultipartFile videoData,
			HttpServletResponse response) {

		if (!videoData.isEmpty() && id > 0) {
			try {
				for (Video ev : videos) {
					logger.log(Level.FINE, "Searching for video: " + ev.getId());
					if (ev.getId() == id) {
						logger.log(Level.FINE, "Video was found: " + ev.getId());
						try {
							logger.info("Saving the data for video object: "
									+ ev.getId());
							vdfm.saveVideoData(ev, videoData.getInputStream());
							videoStatus.setState(VideoState.READY);

						} catch (IOException io) {
							response.setStatus(HttpServletResponse.SC_NOT_FOUND);
							io.printStackTrace();
						}
					}
				}
				return videoStatus;
			} catch (Exception e) {
				logger.log(Level.FINEST, "You failed to upload " + id);
				e.printStackTrace();
			}
		} else {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
		return videoStatus;

	}

	@RequestMapping(value = VIDEO_DATA_PATH, method = RequestMethod.GET)
	public void getData(@PathVariable(ID_PARAMETER) long id,
			HttpServletResponse response) throws Exception {
		if (id > 0) {
			for (Video ev : videos) {
				if (ev.getId() == id) {
					logger.log(Level.INFO, "ev is set: " + ev.getId());
					try {
						vdfm.copyVideoData(ev, response.getOutputStream());
						response.setStatus(HttpServletResponse.SC_OK);
					} catch (FileNotFoundException e) {
						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
						e.printStackTrace();
					} catch (IOException io) {
						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
						io.printStackTrace();
					}
				}
			}
		} else {
			logger.info("Bad Id: 404 return: " + id);
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}

	}

	// helper method that set's the url
	private String getDataUrl(long videoId) {
		String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
		return url;
	}

	private String getUrlBaseForLocalServer() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes()).getRequest();
		String base = "http://"
				+ request.getServerName()
				+ ((request.getServerPort() != 80) ? ":"
						+ request.getServerPort() : "");
		return base;
	}

}
