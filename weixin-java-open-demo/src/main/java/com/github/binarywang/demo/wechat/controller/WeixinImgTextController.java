package com.github.binarywang.demo.wechat.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.github.binarywang.demo.wechat.service.WxOpenServiceDemo;
import com.github.binarywang.demo.wechat.utils.FileMatchUtil;
import com.github.binarywang.demo.wechat.utils.GIfUtil;
import com.lj.cloud.secrity.service.WeixinImgService;
import com.lj.cloud.secrity.service.WeixinImgtextItemService;
import com.lj.cloud.secrity.service.WeixinPushLogService;
import com.lj.cloud.secrity.service.WeixinUserinfoService;
import com.weixindev.micro.serv.common.bean.RestAPIResult2;
import com.weixindev.micro.serv.common.bean.WxMpErrorMsg;
import com.weixindev.micro.serv.common.bean.weixin.WeixinImg;
import com.weixindev.micro.serv.common.bean.weixin.WeixinImgtextItem;
import com.weixindev.micro.serv.common.bean.weixin.WeixinPushLog;
import com.weixindev.micro.serv.common.bean.weixin.WeixinUserinfo;
import com.weixindev.micro.serv.common.pagination.Query;
import com.weixindev.micro.serv.common.util.DateUtil;
import com.weixindev.micro.serv.common.util.FileType;
import com.weixindev.micro.serv.common.util.StringUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.common.exception.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.WxMpMassNews;
import me.chanjar.weixin.mp.bean.WxMpMassPreviewMessage;
import me.chanjar.weixin.mp.bean.WxMpMassTagMessage;
import me.chanjar.weixin.mp.bean.material.WxMediaImgUploadResult;
import me.chanjar.weixin.mp.bean.material.WxMpMaterial;
import me.chanjar.weixin.mp.bean.material.WxMpMaterialUploadResult;
import me.chanjar.weixin.mp.bean.material.WxMpMaterialVideoInfoResult;
import me.chanjar.weixin.mp.bean.result.WxMpMassSendResult;
import me.chanjar.weixin.mp.bean.result.WxMpMassUploadResult;

@Api(value = "服务管理服务", tags = "图文管理服务")
@RestController
public class WeixinImgTextController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private WxOpenServiceDemo wxOpenServiceDemo;
	@Autowired
	WxMpService WxMpService;

	@Autowired
	WeixinUserinfoService WeixinUserinfoService;

	@Autowired
	private WeixinImgtextItemService weixinImgtextItemService;
	
	@Autowired
	private WeixinPushLogService weixinPushLogService ;

	@Value("${appURL}")
	private String appURL;// 网站前台url

	@Value("${file_location}")
	private String file_location;// 文件存储路径
	@Value("${ctxAppWeixin}")
	private String ctxAppWeixin;// 微信网站路径
	
	@Autowired
	private WeixinImgService weixinImgService;
	
	/**
	 * 1、首先，预先将图文消息中需要用到的图片，使用上传图文消息内图片接口，上传成功并获得图片 URL；
	 * 2、上传图文消息素材，需要用到图片时，请使用上一步获取的图片 URL； 3、使用对用户标签的群发，或对 OpenID
	 * 列表的群发，将图文消息群发出去，群发时微信会进行原创校验，并返回群发操作结果；
	 * 4、在上述过程中，如果需要，还可以预览图文消息、查询群发状态，或删除已群发的消息等。
	 */
	@ApiOperation(value = "群发")
	@RequestMapping(value = "/api/doExexcuteBatchSend", method = {RequestMethod.POST})
	public RestAPIResult2 doExexcuteBatchSend(@RequestParam Map<String, String> map) {
		RestAPIResult2 restAPIResult = new RestAPIResult2();
		restAPIResult.setRespCode(1);
		restAPIResult.setRespMsg("成功");

		String imgTextId = String.valueOf(map.get("imgTextId"));// 图文素材ID
		String ids = map.get("ids");// 选择的公众号

		List<String> idsList = StringUtil.splitStringToStringList(ids);

		logger.info("StringUtil.splitStringToStringList :" + idsList.size());

		logger.info("群发消息 imgTextId:" + imgTextId + ",公众号ids:" + ids);
		
		StringBuffer sb = new StringBuffer("");

		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("imgTextId", imgTextId);
			// 查看图文列表项数据
			Query query = new Query(params);
			List<WeixinImgtextItem> list = weixinImgtextItemService.selectByExample(query);

			logger.info("imgTextId= " + imgTextId + " 的记录数据是list.size :" + list.size());

			for (WeixinImgtextItem WeixinImgtextItem : list) {
				if (StringUtils.isBlank(WeixinImgtextItem.getHeadImg())) {
					restAPIResult.setRespCode(0);
					restAPIResult.setRespMsg("封面图片为空，不允许发送");
					return restAPIResult;
				}
			}

			
			for (String str : idsList) {
				int i = 0;
				if (StringUtils.isNotBlank(str)) {// 过滤授权状态为空的
					if (list != null) {
						String appId = str;
						
						WxMpMassNews news = new WxMpMassNews();
						
						try {
							for (WeixinImgtextItem WeixinImgtextItem : list) {
								
								String thumbMediaId = "";
								
								//替换图片 
								if (StringUtil.isNotBlank(WeixinImgtextItem.getArticleContent())) {// 更新media
									List<String> strContentList = FileMatchUtil
											.getImgSrcList(WeixinImgtextItem.getArticleContent());
									String retContent = "";
									retContent = WeixinImgtextItem.getArticleContent();
									WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
	
									if (strContentList != null) {// 替换图片
										for (String strCont : strContentList) {
											System.out.println("图片strCont=" + strCont);
	
											String headImgRepl = strCont.replaceFirst(appURL, file_location);
	
											File fileTmp = new File(headImgRepl);
	
											if(fileTmp!=null &&fileTmp.exists())	{
												WxMediaImgUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
														.getWxMpServiceByAppid(str).getMaterialService()
														.mediaImgUpload(fileTmp);
												String url = res.getUrl();
												retContent = retContent.replaceFirst(strCont, url);// 替换后的内容
		
												WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
												weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);
												logger.info(" 替换图片img WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
														+ ",的imgTextId=" + imgTextId + ", url:" + url + ",retContent:" + retContent);
											}else {
												logger.info(" 替换图片文件路径不存在" + headImgRepl + ",str="+str);
												
												//网络图片地址
												/*URL fileUrl = new URL(str);
												URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
												HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;
												InputStream inputStream = httpUrlConnection.getInputStream();
												InputStream inputStream2 = httpUrlConnection.getInputStream();//两个流，防止被关闭
												
												String extName = FileTypeJudge.getType2(inputStream).name().toLowerCase();
												String fileName = System.nanoTime()+"."+extName; 
												String filePath = file_location +fileName;
												
												FileUtil.createFile(inputStream2, filePath);*/
												
												String extName = StringUtil.getExtension(strCont).toLowerCase();//文件扩展名
												String fileName = System.nanoTime()+"."+extName; 
												String filePath = file_location +fileName;
												
												URL netUrl = new URL(strCont);   
										        
										        if(FileType.GIF.name().toLowerCase().equals(extName)){
											    	URLConnection rulConnection = netUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
													HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;
													InputStream inputStream = httpUrlConnection.getInputStream();
													
													GIfUtil.saveGif(inputStream,filePath);
												}else {
													BufferedImage image = null; 
													image = ImageIO.read(netUrl);    
											        ImageIO.write(image, extName, new File(filePath));   
												}
										        
												
												WeixinImg weixinImg = new WeixinImg();
												
												Integer createBy = 1;
												weixinImg.setCreateBy(createBy);
												weixinImg.setCreateByUname("admin01");
												weixinImg.setCreateDate(DateUtil.getNowDateYYYYMMddHHMMSS());
												
												String headImg = appURL+"/"+fileName;
												weixinImg.setHeadImg(headImg);
												weixinImgService.insertSelective(weixinImg);
												
												
												 fileTmp = new File(filePath);
												 WxMediaImgUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
															.getWxMpServiceByAppid(appId).getMaterialService()
															.mediaImgUpload(fileTmp);
													String url = res.getUrl();
													retContent = retContent.replaceFirst(str, url);// 替换后的内容
					
													WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
													weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);
													logger.info(" 替换图片img WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
															+ ",的imgTextId=" + imgTextId + ", url:" + res.getUrl() + ",retContent:"
															+ retContent);
												
											}
										}
									}
								}
									
								//上传图文
									try {
										URL fileUrl = new URL(WeixinImgtextItem.getHeadImg());

										URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
										HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;

										InputStream inputStream = httpUrlConnection.getInputStream();

										// 动态判断mediaType
										String headImg = WeixinImgtextItem.getHeadImg();

										String mediaType = "image";// 图片类型的素材
										String fileType = StringUtil.getExtension(headImg);// 获得文件的扩展名

										WxMediaUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
												.getWxMpServiceByAppid(str).getMaterialService()
												.mediaUpload(mediaType, fileType, inputStream);

										String mediaId = res.getMediaId();

										logger.info("上传图文消息的封面的mediaId=" + mediaId + ",mediaType=" + mediaType + ",fileType=" + fileType
												+ ",headImg=" + headImg);
										
										thumbMediaId = mediaId;
										
										WeixinImgtextItem.setMediaId(mediaId);
										weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);

										logger.info("上传图文消息的封面的图片  WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
												+ ",的imgTextId=" + imgTextId + ", mediaId= " + mediaId);

										if (inputStream != null) {
											inputStream.close();
										}
									}
										catch(WxErrorException e) {
											 e.printStackTrace();
									    		Integer code = e.getError().getErrorCode();
												logger.info(str + "上传图文消息的封面的图片异常:" +e.getMessage() + ",异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
												sb.append("上传图文消息的封面的图片异常:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
									} catch (Exception e) {
										e.printStackTrace();
										logger.error("上传图文消息的封面的图片，异常信息:" + e.getMessage());
										restAPIResult.setRespCode(0);
										restAPIResult.setRespMsg("上传图文消息的封面的图片，异常信息:" + e.getMessage());
										sb.append("群发异常:" +e.getMessage()+"<br/>");
									}

								
								WxMpMassNews.WxMpMassNewsArticle article2 = new WxMpMassNews.WxMpMassNewsArticle();
								article2.setTitle(WeixinImgtextItem.getTitle());
								article2.setContent(WeixinImgtextItem.getNewContent());// 文章内容为替换以后的文章内容
								article2.setThumbMediaId(thumbMediaId);
								// if (i == 0) {// 默认第一张是封面
								// article2.setShowCoverPic(true);
								// } else {
								// article2.setShowCoverPic(false);
								// }
								article2.setShowCoverPic(false);
								article2.setAuthor(WeixinImgtextItem.getAuthor());
								article2.setContentSourceUrl(WeixinImgtextItem.getOriginUrl());
								article2.setDigest(WeixinImgtextItem.getIntro());
								news.addArticle(article2);
								i++;
							}

							WxMpMassUploadResult massUploadResult = wxOpenServiceDemo.getWxOpenComponentService()
									.getWxMpServiceByAppid(appId).getMassMessageService().massNewsUpload(news);

							logger.info("群发文件素材结果 type:" + massUploadResult.getType() + ",mediaId="
									+ massUploadResult.getMediaId());


							WxMpMassTagMessage WxMpMassTagMessage = new WxMpMassTagMessage();
							WxMpMassTagMessage.setMsgType("mpnews");// 群发 WxConsts.MASS_MSG_NEWS
							WxMpMassTagMessage.setMediaId(massUploadResult.getMediaId());
							// WxMpMassTagMessage.setSendAll(true);

							WxMpMassSendResult massResult = wxOpenServiceDemo.getWxOpenComponentService()
									.getWxMpServiceByAppid(appId).getMassMessageService()
									.massGroupMessageSend(WxMpMassTagMessage);
							
							
							WeixinUserinfo WeixinUserinfoFilter = WeixinUserinfoService.selectByauthorizerAppid(appId);
							
							WeixinPushLog weixinPushLog = new WeixinPushLog();
							weixinPushLog.setCategoryId("mpnews");//图文消息
							weixinPushLog.setMsgId(massResult.getMsgId());
							weixinPushLog.setMsgDataId(massResult.getMsgDataId());
							weixinPushLog.setCreateBy(1);
							weixinPushLog.setCreateByUname("admin01");
							weixinPushLog.setUserId(WeixinUserinfoFilter.getId());
							weixinPushLog.setCreateDate(DateUtil.getNowDateYYYYMMddHHMMSS());
							weixinPushLog.setAuthorizerAppid(appId);
							
							weixinPushLogService.insertSelective(weixinPushLog);

							logger.info("群发消息str= " + str + ",结果：" + massResult.getErrorCode() + ","
									+ massResult.getErrorMsg() + "," + massResult.getMsgId());
						}catch(WxErrorException e) {
							 e.printStackTrace();
					    		Integer code = e.getError().getErrorCode();
								logger.info(str + "群发异常:" +e.getMessage() + ",异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
								sb.append("异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
					    }catch(Exception e){
							sb.append(str + "群发异常:" +e.getMessage()+"<br/>");
						}
						
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("群发异常，异常信息:" + e.getMessage());
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg("群发异常，异常信息:" + e.getMessage());
			sb.append("群发异常:" +e.getMessage()+"<br/>");
		}
		
		if(StringUtils.isNoneBlank(sb.toString())){
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg(sb.toString());
		}

		return restAPIResult;
	}

	/**
	 * 1、首先，预先将图文消息中需要用到的图片，使用上传图文消息内图片接口，上传成功并获得图片 URL；
	 * 2、上传图文消息素材，需要用到图片时，请使用上一步获取的图片 URL； 3、使用对用户标签的群发，或对 OpenID
	 * 列表的群发，将图文消息群发出去，群发时微信会进行原创校验，并返回群发操作结果；
	 * 4、在上述过程中，如果需要，还可以预览图文消息、查询群发状态，或删除已群发的消息等。
	 */
	@ApiOperation(value = "群发同步")
	@RequestMapping(value = "/api/doExexcuteBatchSync", method = {RequestMethod.POST})
	public RestAPIResult2 doExexcuteBatchSync(@RequestParam Map<String, String> map) {
		RestAPIResult2 restAPIResult = new RestAPIResult2();
		restAPIResult.setRespCode(1);
		restAPIResult.setRespMsg("成功");
		
		StringBuffer sb = new StringBuffer("");

		String imgTextId = String.valueOf(map.get("imgTextId"));// 图文素材ID
		String ids = map.get("ids");// 选择的公众号

		List<String> idsList = StringUtil.splitStringToStringList(ids);

		logger.info("StringUtil.splitStringToStringList :" + idsList.size());

		logger.info("同步消息 imgTextId:" + imgTextId + ",公众号ids:" + ids);

		try {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("imgTextId", imgTextId);
			// 查看图文列表项数据
			Query query = new Query(params);
			List<WeixinImgtextItem> list = weixinImgtextItemService.selectByExample(query);

			logger.info("imgTextId= " + imgTextId + " 的记录数据是list.size :" + list.size());

			for (WeixinImgtextItem WeixinImgtextItem : list) {
				if (StringUtils.isBlank(WeixinImgtextItem.getHeadImg())) {
					restAPIResult.setRespCode(0);
					restAPIResult.setRespMsg("封面图片为空，不允许发送");
					return restAPIResult;
				}
			}

			for (String str : idsList) {
				int i = 0;
				WxMpMassNews news = new WxMpMassNews();
				// (1)处理图文消息正文内部的图片、视频、音频等文件信息替换 上传图文消息的正文图片(返回的url拼在正文的<img>标签中)

				for (WeixinImgtextItem WeixinImgtextItem : list) {

					if (StringUtil.isNotBlank(WeixinImgtextItem.getArticleContent())) {// 更新media
						try {

							List<String> strContentList = FileMatchUtil
									.getImgSrcList(WeixinImgtextItem.getArticleContent());
							String retContent = "";
							retContent = WeixinImgtextItem.getArticleContent();
							WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容

							if (strContentList != null) {// 替换图片
								for (String strCon : strContentList) {
									System.out.println("图片strCon=" + strCon);

									String headImgRepl = strCon.replaceFirst(appURL, file_location);

									File fileTmp = new File(headImgRepl);
									
									if(fileTmp!=null &&fileTmp.exists())	{

										WxMediaImgUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
												.getWxMpServiceByAppid(str).getMaterialService()
												.mediaImgUpload(fileTmp);
										String url = res.getUrl();
										retContent = retContent.replaceFirst(strCon, url);// 替换后的内容
	
										WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
										weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);
										logger.info(" 替换图片img WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
												+ ",的imgTextId=" + imgTextId + ", url:" + res.getUrl() + ",retContent:"
												+ retContent);
									}else {
										WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
										logger.info(" 替换图片文件路径不存在" + headImgRepl + ",str="+str);
										
										//网络图片地址
										/*URL fileUrl = new URL(str);
										URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
										HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;
										InputStream inputStream = httpUrlConnection.getInputStream();
										InputStream inputStream2 = httpUrlConnection.getInputStream();//两个流，防止被关闭
										
										String extName = FileTypeJudge.getType2(inputStream).name().toLowerCase();
										String fileName = System.nanoTime()+"."+extName; 
										String filePath = file_location +fileName;
										
										FileUtil.createFile(inputStream2, filePath);*/
										
										String extName = StringUtil.getExtension(strCon).toLowerCase();//文件扩展名
										String fileName = System.nanoTime()+"."+extName; 
										String filePath = file_location +fileName;
								        
								        URL netUrl = new URL(strCon);   
								        
								        if(FileType.GIF.name().toLowerCase().equals(extName)){
									    	URLConnection rulConnection = netUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
											HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;
											InputStream inputStream = httpUrlConnection.getInputStream();
											
											GIfUtil.saveGif(inputStream,filePath);
										}else {
											BufferedImage image = null; 
											image = ImageIO.read(netUrl);    
									        ImageIO.write(image, extName, new File(filePath));   
										}
								        
										
										WeixinImg weixinImg = new WeixinImg();
										
										Integer createBy = 1;
										weixinImg.setCreateBy(createBy);
										weixinImg.setCreateByUname("admin01");
										weixinImg.setCreateDate(DateUtil.getNowDateYYYYMMddHHMMSS());
										
										String headImg = appURL+"/"+fileName;
										weixinImg.setHeadImg(headImg);
										weixinImgService.insertSelective(weixinImg);
										
										
										 fileTmp = new File(filePath);
										 WxMediaImgUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
													.getWxMpServiceByAppid(str).getMaterialService()
													.mediaImgUpload(fileTmp);
											String url = res.getUrl();
											retContent = retContent.replaceFirst(str, url);// 替换后的内容
			
											WeixinImgtextItem.setNewContent(retContent);// 设置替换以后新的内容
											weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);
											logger.info(" 替换图片img WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
													+ ",的imgTextId=" + imgTextId + ", url:" + res.getUrl() + ",retContent:"
													+ retContent);
										
									}
								}
							}

						} catch(WxErrorException e) {
							 e.printStackTrace();
					    		Integer code = e.getError().getErrorCode();
								logger.info(str + "上传图文消息的封面的图片异常:" +e.getMessage() + ",异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
								sb.append("上传图文消息的封面的图片异常:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
					} catch (Exception e) {
							e.printStackTrace();
							logger.error("处理图文消息正文内部的图片、视频、音频等文件信息替换，异常信息:" + e.getMessage());
							restAPIResult.setRespCode(0);
							restAPIResult.setRespMsg("处理图文消息正文内部的图片、视频、音频等文件信息替换，异常信息:" + e.getMessage());
							sb.append("上传图文消息的封面的图片异常:" + e.getMessage()+"<br/>");
						}

					}
				}

				// (2) 上传图文消息的封面的图片
				for (WeixinImgtextItem WeixinImgtextItem : list) {
					if (StringUtil.isNotBlank(WeixinImgtextItem.getHeadImg())) {// 更新media
							
						try {
							URL fileUrl = new URL(WeixinImgtextItem.getHeadImg());

							URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
							HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;

							InputStream inputStream = httpUrlConnection.getInputStream();

							// 动态判断mediaType
							String headImg = WeixinImgtextItem.getHeadImg();

							String mediaType = "image";// 图片类型的素材
							String mediaId = "";

							String fileType = StringUtil.getExtension(headImg);// 获得文件的扩展名

							WxMediaUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
									.getWxMpServiceByAppid(str).getMaterialService()
									.mediaUpload(mediaType, fileType, inputStream);

							mediaId = res.getMediaId();
							logger.info("上传图文消息的封面的mediaId=" + mediaId + ",mediaType=" + mediaType + ",fileType=" + fileType
									+ ",headImg=" + headImg);

							WeixinImgtextItem.setMediaId(mediaId);
							weixinImgtextItemService.updateByPrimaryKeySelective(WeixinImgtextItem);

							logger.info("上传图文消息的封面的图片  WeixinImgtextItem.getId 的ID为=" + WeixinImgtextItem.getId()
									+ ",的imgTextId=" + imgTextId + ", mediaId= " + mediaId);

							if (inputStream != null) {
								inputStream.close();
							}
						} catch(WxErrorException e) {
							 e.printStackTrace();
					    		Integer code = e.getError().getErrorCode();
								logger.info(str + "上传图文消息的封面的图片异常:" +e.getMessage() + ",异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
								sb.append("上传图文消息的封面的图片异常:" + WxMpErrorMsg.findMsgByCode(code)+"<br/>");
						} catch (Exception e) {
							e.printStackTrace();
							logger.error("上传图文消息的封面的图片，异常信息:" + e.getMessage());
							restAPIResult.setRespCode(0);
							restAPIResult.setRespMsg("上传图文消息的封面的图片，异常信息:" + e.getMessage());
							sb.append("上传图文消息的封面的图片异常:" + e.getMessage()+"<br/>");
						}

					}
				}
				
				if (StringUtils.isNotBlank(str)) {// 过滤授权状态为空的
					if (list != null) {
						String appId = str;

						try {
							for (WeixinImgtextItem WeixinImgtextItem : list) {
								WxMpMassNews.WxMpMassNewsArticle article2 = new WxMpMassNews.WxMpMassNewsArticle();
								article2.setTitle(WeixinImgtextItem.getTitle());
								article2.setContent(WeixinImgtextItem.getNewContent());// 文章内容为替换以后的文章内容
								article2.setThumbMediaId(WeixinImgtextItem.getMediaId());
								// if (i == 0) {// 默认第一张是封面
								// article2.setShowCoverPic(true);
								// } else {
								// article2.setShowCoverPic(false);
								// }
								article2.setShowCoverPic(false);
								article2.setAuthor(WeixinImgtextItem.getAuthor());
								article2.setContentSourceUrl(WeixinImgtextItem.getOriginUrl());
								article2.setDigest(WeixinImgtextItem.getIntro());
								news.addArticle(article2);
								i++;
							}

							WxMpMassUploadResult massUploadResult = wxOpenServiceDemo.getWxOpenComponentService()
									.getWxMpServiceByAppid(appId).getMassMessageService().massNewsUpload(news);

							logger.info("同步文件素材结果 type:" + massUploadResult.getType() + ",mediaId="
									+ massUploadResult.getMediaId());
							
							WeixinUserinfo WeixinUserinfoFilter = WeixinUserinfoService.selectByauthorizerAppid(appId);
							WeixinPushLog weixinPushLog = new WeixinPushLog();
							weixinPushLog.setCategoryId("mpnews");//图文消息
							weixinPushLog.setMediaCategory("1");//永久素材
							weixinPushLog.setMediaId(massUploadResult.getMediaId());
							weixinPushLog.setImgTextId(Integer.parseInt(imgTextId));
							weixinPushLog.setCreateBy(1);
							weixinPushLog.setCreateByUname("admin01");
							weixinPushLog.setUserId(WeixinUserinfoFilter.getId());
							weixinPushLog.setAuthorizerAppid(appId);
							
							weixinPushLog.setCreateDate(DateUtil.getNowDateYYYYMMddHHMMSS());
							
							weixinPushLogService.insertSelective(weixinPushLog);
						} catch(WxErrorException e) {
							 e.printStackTrace();
					    		Integer code = e.getError().getErrorCode();
								logger.info(str + "同步异常:" +e.getMessage() + ",异常信息:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
								sb.append("同步异常:"+ WxMpErrorMsg.findMsgByCode(code)+"<br/>");
						} catch (Exception e2) {
							logger.error("同步异常，异常信息22222222222222:" + e2.getMessage());
							sb.append("同步异常:"+ e2.getMessage()+"<br/>");
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("同步异常，异常信息:" + e.getMessage());
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg("同步异常，异常信息:" + e.getMessage());
			sb.append("同步异常:"+ e.getMessage()+"<br/>");
		}

		if(StringUtils.isNoneBlank(sb.toString())){
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg(sb.toString());
		}
		
		return restAPIResult;
	}
	
	
	/*
	 * 预览 新版 在用版
	 */
	@GetMapping("/test1")
	public RestAPIResult2 test1(Integer templateid, HttpServletRequest request) {
		WeixinUserinfo weixinUserinfo = WeixinUserinfoService.selectByPrimaryKey(37);
		RestAPIResult2 restAPIResult = new RestAPIResult2();
		restAPIResult.setRespCode(0);
		restAPIResult.setRespMsg("预览发送失败");
		try {
			String headImgRepl = file_location + "1383730101859642.mp3";
			File fileTmp = new File(headImgRepl);
			String mediaType = "voice";// voice
			String name = "Test tile";
			String videoTitle = "测试内容";
			String videoIntroduction = "测试介绍";

			WxMpMaterial WxMpMaterial = new WxMpMaterial(name, fileTmp, videoTitle, videoIntroduction);

			logger.info("音频测试传输信息:headImgRepl:" + headImgRepl);

			WxMpMaterialUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.materialFileUpload(mediaType, WxMpMaterial);

			String mediaId = res.getMediaId();

			logger.info("音频上传结果  mediaId:" + mediaId);
			restAPIResult.setDataCode(mediaId);

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("测试异常，异常信息22222222:" + e.getMessage());
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg("测试异常，异常信息22222222:" + e.getMessage());
		}

		return restAPIResult;
	}

	@GetMapping("/test2")
	public WxMpMaterialVideoInfoResult test2(String mediaId, HttpServletRequest request) {
		WeixinUserinfo weixinUserinfo = WeixinUserinfoService.selectByPrimaryKey(37);
		WxMpMaterialVideoInfoResult materialVideoInfo = new WxMpMaterialVideoInfoResult();
		try {
			materialVideoInfo = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.materialVideoInfo(mediaId);

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("测试异常，异常信息22222222:" + e.getMessage());
		}

		return materialVideoInfo;
	}

	/**
	 * 视频流读取
	 * 
	 * @param id
	 * @param response
	 * @throws Exception
	 */
	@RequestMapping("/getVoice")
	public @ResponseBody void getVoice(String mediaId, HttpServletResponse response) throws Exception {

		try {
			WeixinUserinfo weixinUserinfo = WeixinUserinfoService.selectByPrimaryKey(37);

			InputStream in = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.materialImageOrVoiceDownload(mediaId);

			ServletOutputStream out = response.getOutputStream();
			byte[] b = null;
			while (in.available() > 0) {
				if (in.available() > 10240) {
					b = new byte[10240];
				} else {
					b = new byte[in.available()];
				}
				in.read(b, 0, b.length);
				out.write(b, 0, b.length);
			}
			in.close();
			out.flush();
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("获得音频异常" + e.getMessage());
		}
	}

	/*
	 * 预览 新版 在用版
	 */
	@GetMapping("/test3")
	public RestAPIResult2 test3(HttpServletRequest request, String nike_name) {
		WeixinUserinfo weixinUserinfo = WeixinUserinfoService.selectByPrimaryKey(37);
		RestAPIResult2 restAPIResult = new RestAPIResult2();
		restAPIResult.setRespCode(0);
		restAPIResult.setRespMsg("预览发送失败");
		try {

			String articleContent = "音频测试: <audio src=\"http://weixin.xrtz.org:8050/weixin/getVoice?mediaId=UFyprEXBvieSOBZuNARuqdDwaHZ4GW_KKVe35csoHDw\"></audio>";
			String headImgRepl = file_location + "1383730101859642.mp3";
			File fileTmp = new File(headImgRepl);
			String mediaType = "voice";// voice
			String name = "Test tile";
			String videoTitle = "测试内容";
			String videoIntroduction = "测试介绍";

			WxMpMaterial WxMpMaterial = new WxMpMaterial(name, fileTmp, videoTitle, videoIntroduction);
			logger.info("音频测试传输信息:articleContent:" + articleContent + ",headImgRepl:" + headImgRepl);

			WxMpMaterialUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.materialFileUpload(mediaType, WxMpMaterial);

			String mediaId = res.getMediaId();

			logger.info("音频上传结果articleContent:" + articleContent + ",mediaId:" + mediaId);

			URL fileUrl = new URL("http://weixin.xrtz.org:8022/1093903287960825.png");

			URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
			HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;

			InputStream inputStream = httpUrlConnection.getInputStream();

			// 上传封面图片
			String headImg = "http://weixin.xrtz.org:8022/1093903287960825.png";

			String mediaType2 = "image";// 图片类型的素材

			String fileType = StringUtil.getExtension(headImg);// 获得文件的扩展名

			WxMediaUploadResult res2 = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.mediaUpload(mediaType2, fileType, inputStream);

			String mediaId2 = res2.getMediaId();
			logger.info("上传图文消息的封面的mediaId2=" + mediaId2 + ",mediaType2=" + mediaType2 + ",fileType=" + fileType
					+ ",headImg=" + headImg);

			if (inputStream != null) {
				inputStream.close();
			}

			WxMpMassNews news = new WxMpMassNews();

			WxMpMassNews.WxMpMassNewsArticle article2 = new WxMpMassNews.WxMpMassNewsArticle();
			article2.setTitle("testTitle");
			article2.setContent(articleContent);// 文章内容为替换以后的文章内容
			article2.setThumbMediaId(mediaId2);
			article2.setShowCoverPic(true);
			article2.setAuthor("");
			article2.setContentSourceUrl("");
			article2.setDigest("");
			news.addArticle(article2);

			WxMpMassUploadResult massUploadResult = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMassMessageService()
					.massNewsUpload(news);

			logger.info("预览文件素材结果 type:" + massUploadResult.getType() + ",mediaId=" + massUploadResult.getMediaId());

			WxMpMassPreviewMessage wxMpMassPreviewMessage = new WxMpMassPreviewMessage();
			wxMpMassPreviewMessage.setToWxUserName(nike_name);
			wxMpMassPreviewMessage.setMsgType("mpnews");
			wxMpMassPreviewMessage.setMediaId(massUploadResult.getMediaId());

			WxMpMassSendResult wxMpMassSendResult = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMassMessageService()
					.massMessagePreview(wxMpMassPreviewMessage);
			System.out.println("---------------------------------预览消息结果--------------------------------");
			System.out.println(wxMpMassSendResult.getMsgId());
			System.out.println(wxMpMassSendResult.getErrorCode());
			System.out.println(wxMpMassSendResult.getErrorMsg());
			System.out.println("---------------------------------预览消息结果--------------------------------");
			if (wxMpMassSendResult.getErrorCode().equals("0")) {
				restAPIResult.setRespCode(1);
				restAPIResult.setRespMsg("preview success");
			} else {
				restAPIResult.setRespCode(0);
				restAPIResult.setRespMsg(
						"预览异常，异常信息:" + wxMpMassSendResult.getErrorMsg() + wxMpMassSendResult.getErrorCode());
			}

			logger.info("同步文件素材结果 type:" + massUploadResult.getType() + ",mediaId=" + massUploadResult.getMediaId());

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("测试异常，异常信息22222222333333333333333:" + e.getMessage());
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg("测试异常，异常信息22222222:" + e.getMessage());
		}

		return restAPIResult;
	}

	@GetMapping("/test4")
	public RestAPIResult2 test4(HttpServletRequest request, String nike_name) {
		WeixinUserinfo weixinUserinfo = WeixinUserinfoService.selectByPrimaryKey(37);
		RestAPIResult2 restAPIResult = new RestAPIResult2();
		restAPIResult.setRespCode(0);
		restAPIResult.setRespMsg("预览发送失败");
		try {

			String articleContent = "音频测试04: <audio src=\"http://weixin.xrtz.org:8022/1383730101859642.mp3\" controls=\"controls\"></audio>";
			String headImgRepl = file_location + "1383730101859642.mp3";
			File fileTmp = new File(headImgRepl);
			String mediaType = "voice";// voice
			String name = "Test tile";
			String videoTitle = "测试内容";
			String videoIntroduction = "测试介绍";

			WxMpMaterial WxMpMaterial = new WxMpMaterial(name, fileTmp, videoTitle, videoIntroduction);
			logger.info("音频测试传输信息:articleContent:" + articleContent + ",headImgRepl:" + headImgRepl);

			WxMpMaterialUploadResult res = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.materialFileUpload(mediaType, WxMpMaterial);

			String mediaId = res.getMediaId();

			logger.info("音频上传结果articleContent:" + articleContent + ",mediaId:" + mediaId);

			URL fileUrl = new URL("http://weixin.xrtz.org:8022/1093903287960825.png");

			URLConnection rulConnection = fileUrl.openConnection();// 此处的urlConnection对象实际上是根据URL的
			HttpURLConnection httpUrlConnection = (HttpURLConnection) rulConnection;

			InputStream inputStream = httpUrlConnection.getInputStream();

			// 上传封面图片
			String headImg = "http://weixin.xrtz.org:8022/1093903287960825.png";

			String mediaType2 = "image";// 图片类型的素材

			String fileType = StringUtil.getExtension(headImg);// 获得文件的扩展名

			WxMediaUploadResult res2 = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMaterialService()
					.mediaUpload(mediaType2, fileType, inputStream);

			String mediaId2 = res2.getMediaId();
			logger.info("上传图文消息的封面的mediaId2=" + mediaId2 + ",mediaType2=" + mediaType2 + ",fileType=" + fileType
					+ ",headImg=" + headImg);

			if (inputStream != null) {
				inputStream.close();
			}

			WxMpMassNews news = new WxMpMassNews();

			WxMpMassNews.WxMpMassNewsArticle article2 = new WxMpMassNews.WxMpMassNewsArticle();
			article2.setTitle("testTitle");
			article2.setContent(articleContent);// 文章内容为替换以后的文章内容
			article2.setThumbMediaId(mediaId2);
			article2.setShowCoverPic(true);
			article2.setAuthor("");
			article2.setContentSourceUrl("");
			article2.setDigest("");
			news.addArticle(article2);

			WxMpMassUploadResult massUploadResult = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMassMessageService()
					.massNewsUpload(news);

			logger.info("预览文件素材结果 type:" + massUploadResult.getType() + ",mediaId=" + massUploadResult.getMediaId());

			WxMpMassPreviewMessage wxMpMassPreviewMessage = new WxMpMassPreviewMessage();
			wxMpMassPreviewMessage.setToWxUserName(nike_name);
			wxMpMassPreviewMessage.setMsgType("mpnews");
			wxMpMassPreviewMessage.setMediaId(massUploadResult.getMediaId());

			WxMpMassSendResult wxMpMassSendResult = wxOpenServiceDemo.getWxOpenComponentService()
					.getWxMpServiceByAppid(weixinUserinfo.getAuthorizerAppid()).getMassMessageService()
					.massMessagePreview(wxMpMassPreviewMessage);
			System.out.println("---------------------------------预览消息结果--------------------------------");
			System.out.println(wxMpMassSendResult.getMsgId());
			System.out.println(wxMpMassSendResult.getErrorCode());
			System.out.println(wxMpMassSendResult.getErrorMsg());
			System.out.println("---------------------------------预览消息结果--------------------------------");
			if (wxMpMassSendResult.getErrorCode().equals("0")) {
				restAPIResult.setRespCode(1);
				restAPIResult.setRespMsg("preview success");
			} else {
				restAPIResult.setRespCode(0);
				restAPIResult.setRespMsg(
						"预览异常，异常信息:" + wxMpMassSendResult.getErrorMsg() + wxMpMassSendResult.getErrorCode());
			}

			logger.info("同步文件素材结果 type:" + massUploadResult.getType() + ",mediaId=" + massUploadResult.getMediaId());

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("测试异常，异常信息22222222333333333333333:" + e.getMessage());
			restAPIResult.setRespCode(0);
			restAPIResult.setRespMsg("测试异常，异常信息22222222:" + e.getMessage());
		}

		return restAPIResult;
	}
}
