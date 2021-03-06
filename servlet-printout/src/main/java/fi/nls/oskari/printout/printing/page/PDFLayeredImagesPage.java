package fi.nls.oskari.printout.printing.page;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDPixelMap;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.apache.pdfbox.pdmodel.markedcontent.PDPropertyList;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.renderer.lite.RendererUtilities;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;

import fi.nls.oskari.printout.printing.PDFProducer.Options;
import fi.nls.oskari.printout.printing.PDFProducer.Page;

/**
 * This class embeds layers of map images as PDF optional contents.
 * 
 * @todo fix to use transforms instead of manual cm to pixel mappings
 */
public class PDFLayeredImagesPage extends PDFAbstractPage implements PDFPage {

	private CoordinateReferenceSystem crs;
	private List<BufferedImage> images;
	private int width;
	private int height;
	private Envelope env;
	private Point centre;

	public PDFLayeredImagesPage(Page page, Options opts, PDFont font,
			CoordinateReferenceSystem coordinateReferenceSystem,
			List<BufferedImage> images, int width, int height, Envelope env,
			Point centre) throws IOException {
		super(page, opts,font);
		this.crs = coordinateReferenceSystem;
		this.images = images;
		this.width = width;
		this.height = height;
		this.env = env;
		this.centre = centre;
	}

	/**
	 * PDXObjectImage must be created before optional content groups are created
	 * 
	 * @param targetDoc
	 * @param ximages
	 * @param images
	 * @throws IOException
	 */
	void createMapLayersImages(PDDocument targetDoc,
			List<PDXObjectImage> ximages, List<BufferedImage> images)
			throws IOException {

		for (BufferedImage image : images) {
			PDXObjectImage ximage = new PDPixelMap(targetDoc, image);

			ximages.add(ximage);
		}

	}

	/**
	 * Let's create overlayers for each map layer
	 * 
	 * @param targetDoc
	 * @param contentStream
	 * @param ocprops
	 * @param props
	 * @param ximages
	 * @param width
	 * @param height
	 * @throws IOException
	 */
	void createMapLayersOverlay(PDDocument targetDoc,
			PDPageContentStream contentStream,
			PDOptionalContentProperties ocprops, PDPropertyList props,
			List<PDXObjectImage> ximages, int width, int height)
			throws IOException {

		int r = 0;
		for (PDXObjectImage ximage : ximages) {
			r++;

			PDOptionalContentGroup layerGroup = new PDOptionalContentGroup(
					"layer" + r);
			ocprops.addGroup(layerGroup);

			COSName mc0 = COSName.getPDFName("MC" + r);
			props.putMapping(mc0, layerGroup);

			contentStream.beginMarkedContentSequence(COSName.OC, mc0);
			contentStream.drawXObject(ximage, 1.0f / 2.54f * 72f,
					1.5f / 2.54f * 72f, width, height);
			contentStream.endMarkedContentSequence();

		}
	}

	/**
	 * 
	 * @param targetDoc
	 * @param images
	 * @param width
	 * @param height
	 * @param centre
	 * @param env
	 * @throws IOException
	 * @throws TransformException
	 */
	
	public void createPages(PDDocument targetDoc) throws IOException,
			TransformException {

		PDDocumentCatalog catalog = targetDoc.getDocumentCatalog();

		PDPage targetPage = page.createNewPageTo(targetDoc);

		PDResources resources = targetPage.findResources();
		if (resources == null) {
			resources = new PDResources();
			targetPage.setResources(resources);
		}

		PDOptionalContentProperties ocprops = catalog.getOCProperties();

		if (ocprops == null) {
			ocprops = new PDOptionalContentProperties();
			catalog.setOCProperties(ocprops);
		}

		PDPropertyList props = new PDPropertyList();
		resources.setProperties(props);

		List<PDXObjectImage> ximages = new ArrayList<PDXObjectImage>(
				images.size());

		/* these MUST be created before optional overlay content */
		createMapLayersImages(targetDoc, ximages, images);

		PDPageContentStream contentStream = page.createContentStreamTo(
				targetDoc, targetPage);

		createMapLayersOverlay(targetDoc, contentStream, ocprops, props,
				ximages, width, height);
		createTextLayerOverlay(targetDoc, contentStream, ocprops, props, env,
				centre, width, height);

	}

	/**
	 * print scale line to show metric extent actual scale matches display scale
	 * in openlayers which
	 * 
	 * 
	 * @param targetDoc
	 * @param contentStream
	 * @param ocprops
	 * @param props
	 * @param font
	 * @param centre
	 * @param env
	 * @param height
	 * @param width
	 * @throws IOException
	 * @throws TransformException
	 */
	void createScaleLine(PDDocument targetDoc,
			PDPageContentStream contentStream,
			PDOptionalContentProperties ocprops, PDPropertyList props,
			PDFont font, Envelope env, Point centre, int width, int height)
			throws IOException, TransformException {
		contentStream.setNonStrokingColor(0, 0, 0);

		contentStream.setStrokingColor(0, 0, 0);

		final ReferencedEnvelope bounds = new ReferencedEnvelope(env.getMinX(),
				env.getMaxX(), env.getMinY(), env.getMaxY(), crs);

		final Rectangle rect = new Rectangle(0, 0, width, height);

		final AffineTransform transform = RendererUtilities
				.worldToScreenTransform(bounds, rect, crs);

		/* krhm... to be fixed with some algoriddim */
		/* time restricted coding... */
		long widthInMeters = Double.valueOf(env.getWidth()).longValue();
		long scaleLenSelector100m = widthInMeters / 100;
		long scaleLenSelector1Km = widthInMeters / 1000;
		long scaleLenSelector5Km = widthInMeters / 5000;
		long scaleLenSelector10Km = widthInMeters / 10000;
		long scaleLenSelector50Km = widthInMeters / 50000;
		long scaleLenSelector100Km = widthInMeters / 100000;
		long scaleLenSelector500Km = widthInMeters / 500000;
		long scaleLenSelector1000Km = widthInMeters / 1000000;

		String scaleText = "";
		long scaleLength = 0;
		if (scaleLenSelector100m == 0) {
			/* m */
			scaleLength = 1;
			scaleText = "1m";
		} else if (scaleLenSelector1Km == 0) {
			/* 10m */
			scaleLength = 10;
			scaleText = "10m";
		} else if (scaleLenSelector5Km == 0) {
			/* 10m */
			scaleLength = 100;
			scaleText = "100m";
		} else if (scaleLenSelector10Km == 0) {
			/* 100m */
			scaleLength = 100;
			scaleText = "100m";
		} else if (scaleLenSelector50Km == 0) {
			/* 10km */
			scaleLength = 1000;
			scaleText = "1km";
		} else if (scaleLenSelector100Km == 0) {
			/* 10km */
			scaleLength = 10000;
			scaleText = "10km";
		} else if (scaleLenSelector500Km == 0) {
			/* 100km */
			scaleLength = 10000;
			scaleText = "10km";
		} else if (scaleLenSelector1000Km == 0) {
			/* 100km */
			scaleLength = 100000;
			scaleText = "100km";
		} else {
			/* 1000km */
			scaleLength = 100000;
			scaleText = "100km";
		}

		double[] srcPts = new double[] { env.getMinX(), env.getMaxY(),
				env.getMaxX(), env.getMinY(), env.getMinX() + scaleLength,
				env.getMinY() };
		double[] dstPts = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
		transform.transform(srcPts, 0, dstPts, 0, 3);

		createTextAtTarget(contentStream, scaleText, 4.0f / 2.54f * 72f, 16f,
				opts.getFontSize(), 0, 0, 0);

		contentStream.addLine(6.0f / 2.54f * 72f, 16f, 6.0f / 2.54f * 72f, 32f);

		contentStream.addLine(6.0f / 2.54f * 72f, 16f,
				6.0f / 2.54f * 72f + Double.valueOf(dstPts[4]).floatValue(),
				16f);

		contentStream.addLine(6.0f / 2.54f * 72f + Double.valueOf(dstPts[4])
				.floatValue(), 16f,
				6.0f / 2.54f * 72f + Double.valueOf(dstPts[4]).floatValue(),
				32f);

		contentStream.closeAndStroke();

	}

	/**
	 * logo , title, etc are put on another optional overlay
	 * 
	 * @param targetDoc
	 * @param contentStream
	 * @param ocprops
	 * @param props
	 * @param centre
	 * @param env
	 * @param height
	 * @param width
	 * @throws IOException
	 * @throws TransformException
	 */
	void createTextLayerOverlay(PDDocument targetDoc,
			PDPageContentStream contentStream,
			PDOptionalContentProperties ocprops, PDPropertyList props,
			Envelope env, Point centre, int width, int height)
			throws IOException, TransformException {

		float logoWidth = 16;
		float logoHeight = 16;

		PDXObjectImage xlogo = null;

		if (opts.isPageLogo()) {
			/* MUST create before optiona content group is created */
			/*
			 * - this is a googled fix to not being able to show images in
			 * overlays
			 */
			InputStream inp = getClass().getResourceAsStream("logo.png");
			try {
				BufferedImage imageBuf = ImageIO.read(inp);
				int w = imageBuf.getWidth(null);
				int h = imageBuf.getHeight(null);
				BufferedImage bi = new BufferedImage(w, h,
						BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D g = (Graphics2D) bi.getGraphics();
				g.drawImage(imageBuf, 0, 0, null);
				g.dispose();

				bi = doScaleWithFilters(bi, (int) logoWidth * 4,
						(int) logoHeight * 4);

				xlogo = new PDPixelMap(targetDoc, bi);
			} finally {
				inp.close();
			}
		}

		PDOptionalContentGroup layerGroup = new PDOptionalContentGroup(
				"overlay");
		ocprops.addGroup(layerGroup);

		COSName mc0 = COSName.getPDFName("MCoverlay");
		props.putMapping(mc0, layerGroup);
		/*PDFont font = PDType1Font.HELVETICA_BOLD;*/
		contentStream.beginMarkedContentSequence(COSName.OC, mc0);

		/* BEGIN overlay content */

		/* title */
		if (opts.getPageTitle() != null) {
			String pageTitle = StringEscapeUtils.unescapeHtml4(Jsoup.clean(
					opts.getPageTitle(), Whitelist.simpleText()));

			createTextAtTarget(contentStream, pageTitle, 1.0f / 2.54f * 72f,
					page.getHeightTargetInPoints() + 50, 
					opts.getFontSize(), 0, 0, 0);
		}

		/* pvm */
		if (opts.isPageDate()) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Locale l = new Locale("fi");
			Date dte = Calendar.getInstance(l).getTime();

			String dateStr = sdf.format(dte);

			createTextAtTarget(contentStream, dateStr,
					page.getWidthTargetInPoints() - 40,
					page.getHeightTargetInPoints() + 50, 
					opts.getFontSize(), 0, 0, 0);

		}

		/* mittakaava */
		if (opts.isPageScale() && crs != null) {

			createScaleLine(targetDoc, contentStream, ocprops, props, font,
					env, centre, width, height);

		}

		/* logo */
		if (opts.isPageLogo()) {
			contentStream.setNonStrokingColor(255, 255, 255);
			contentStream.setStrokingColor(255, 255, 255);

			contentStream.drawXObject(xlogo, 1.0f / 2.54f * 72f, 16, logoWidth,
					logoHeight);

		}

		/* END overlay content */

		contentStream.endMarkedContentSequence();

		contentStream.close();

	}

}
