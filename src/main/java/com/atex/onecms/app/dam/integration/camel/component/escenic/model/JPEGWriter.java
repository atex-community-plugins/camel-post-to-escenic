package com.atex.onecms.app.dam.integration.camel.component.escenic.model;

/**
 * @author peterabjohns
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.bytesource.ByteSource;
import org.apache.commons.imaging.common.bytesource.ByteSourceArray;
import org.apache.commons.imaging.formats.jpeg.JpegConstants;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcBlock;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcConstants;
import org.apache.commons.imaging.formats.jpeg.iptc.IptcParser;
import org.apache.commons.imaging.formats.jpeg.iptc.PhotoshopApp13Data;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegRewriter;
import org.apache.commons.io.IOUtils;


/**
 * Class responsible to write JPEG files including EXIF and IPTC metadata.
 * IPTC metadata are rewritten every time.
 * We can't use buffered image because EXIF metadata and IPTC are not included.
 */
public class JPEGWriter extends JpegRewriter {

	/**
	 * Pieces.
	 */
	private List<JFIFPiece> m_pieces;
	private IPTCMetadata m_metadata;
	private byte[] imageData;


	/**
	 * Replaces the IPTC metadata from the image. The whole App13 segment is replaced.
	 * @param newIPTC the new IPTC metadata
	 * @throws IOException if the metadata cannot be replaced
	 * @throws ImageWriteException if the metadata cannot be replaced
	 */
	public void replaceIPTCMetadata(PhotoshopApp13Data newIPTC) throws IOException, ImageWriteException {
		List newPieces = removePhotoshopApp13Segments(m_pieces);


		// discard old iptc blocks.
		List newBlocks = newIPTC.getNonIptcBlocks();
		byte[] newBlockBytes = new IptcParser().writeIPTCBlock(newIPTC.getRecords());

		int blockType = IptcConstants.IMAGE_RESOURCE_BLOCK_IPTC_DATA;
		byte[] blockNameBytes = new byte[0];
		IptcBlock newBlock = new IptcBlock(blockType, blockNameBytes, newBlockBytes);
		newBlocks.add(newBlock);

		newIPTC = new PhotoshopApp13Data(newIPTC.getRecords(), newBlocks);

		byte segmentBytes[] = new IptcParser()
			.writePhotoshopApp13Segment(newIPTC);
		JFIFPieceSegment newSegment = new JFIFPieceSegment(
			JpegConstants.JPEG_APP13_MARKER, segmentBytes);

		m_pieces = insertAfterLastAppSegments(newPieces, Arrays
			.asList(newSegment));

	}

	/**
	 * Writes the new JPEG to the given output stream. The stream is not closed.
	 * The IPTC metadata are replaced if there were modified.
	 * @param fos the output stream
	 * @throws IOException if the image can't be written
	 */
	public void write(OutputStream fos) throws IOException {
		try {
			PhotoshopApp13Data data = m_metadata.getPhotoshopApp13Data();
			if (data != null) {
				replaceIPTCMetadata(data);
			}

			// Write the XMP segment too, if any
			String xmp = m_metadata.getXmp();
			if (xmp != null) {
				List newPieces = new ArrayList();
				m_pieces = removeXmpSegments(m_pieces);
				int segmentSize = Math.min(xmp.getBytes().length, JpegConstants.MAX_SEGMENT_SIZE);
				byte segmentData[] = writeXmpSegment(m_metadata.getXmp().getBytes(), 0, segmentSize);
				newPieces.add(new JFIFPieceSegment(JpegConstants.JPEG_APP1_MARKER, segmentData));
				m_pieces = insertAfterLastAppSegments(m_pieces, newPieces);
			}

			writeSegments(fos, m_pieces);
		} catch (ImageWriteException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/**
	 * Initializes the writer with the given image.
	 * @param is the image
	 * @throws IOException if the image cannot be read
	 */
	public void load(InputStream is) throws IOException, ImageReadException {

		imageData = IOUtils.toByteArray(is);

		m_metadata = new IPTCMetadata( Imaging.getMetadata(imageData));

		ByteSource source = new ByteSourceArray(imageData);


		try {
			m_pieces = analyzeJFIF(source).pieces;
		} catch (ImageReadException e) {
			throw new IOException(e.getMessage(), e);
		}
	}


	private byte[] writeXmpSegment(byte xmpXmlData[], int start, int length)
		throws IOException
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		os.write(JpegConstants.XMP_IDENTIFIER.toByteArray());
		os.write(xmpXmlData, start, length);

		return os.toByteArray();
	}

	public IPTCMetadata getIPTCMetadata() {


		return m_metadata;
	}

	@Override
	public String toString() {

		return m_pieces.toString();
	}
}