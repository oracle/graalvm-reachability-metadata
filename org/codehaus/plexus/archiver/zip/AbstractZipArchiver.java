package org.codehaus.plexus.archiver.zip;

/**
 *
 * Copyright 2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

import com.sun.org.apache.xalan.internal.xsltc.compiler.sym;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipEncoding;
import org.apache.commons.compress.archivers.zip.ZipEncodingHelper;
import org.codehaus.plexus.archiver.AbstractArchiver;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.UnixStat;
import org.codehaus.plexus.archiver.util.ResourceUtils;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.components.io.resources.PlexusIoSymlink;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * @version $Revision$ $Date$
 */
@SuppressWarnings({"NullableProblems", "UnusedDeclaration"})
public abstract class AbstractZipArchiver
    extends AbstractArchiver
{

    private String comment;

    /**
     * Encoding to use for filenames, defaults to the platform's
     * default encoding.
     */
    private String encoding;

    private boolean doCompress = true;

    private boolean recompressAddedZips = true;

    private boolean doUpdate = false;

    // shadow of the above if the value is altered in execute
    private boolean savedDoUpdate = false;

    protected String archiveType = "zip";

    /*
     * Whether the original compression of entries coming from a ZIP
     * archive should be kept (for example when updating an archive).
     */
    //not used: private boolean keepCompression = false;
    private boolean doFilesonly = false;

    protected final Hashtable<String, String> entries = new Hashtable<String, String>();

    protected final Hashtable<String, String> addedDirs = new Hashtable<String, String>();

    private static final long EMPTY_CRC = new CRC32().getValue();

    protected boolean doubleFilePass = false;

    protected boolean skipWriting = false;

    /**
     * @deprecated Use {@link Archiver#setDuplicateBehavior(String)} instead.
     */
    protected final String duplicate = Archiver.DUPLICATES_SKIP;

    /**
     * true when we are adding new files into the Zip file, as opposed
     * to adding back the unchanged files
     */
    protected boolean addingNewFiles = false;

    /**
     * Whether the file modification times will be rounded up to the
     * next even number of seconds.
	 *
	 * <p>Zip archives store file modification times with a
	 * granularity of two seconds, so the times will either be rounded
	 * up or down.  If you round down, the archive will always seem
	 * out-of-date when you rerun the task, so the default is to round
	 * up.  Rounding up may lead to a different type of problems like
	 * JSPs inside a web archive that seem to be slightly more recent
	 * than precompiled pages, rendering precompilation useless.</p>	 *
	 *
	 * plexus-archiver chooses to round up.
	 *
	 * Java versions up to java7 round timestamp down, which means we add a heuristic value (which is slightly questionable)
	 * Java versions from 8 and up round timestamp up.
	 * s
     */
    private static final boolean isJava7OrLower = Integer.parseInt(System.getProperty("java.version").split("\\.")[1]) <= 7;

	// Renamed version of original file, if it exists
    private File renamedFile = null;

    private File zipFile;

    private boolean success;

    private ZipArchiveOutputStream zOut;

    public String getComment()
    {
        return comment;
    }

    public void setComment( String comment )
    {
        this.comment = comment;
    }

    public String getEncoding()
    {
        return encoding;
    }

    public void setEncoding( String encoding )
    {
        this.encoding = encoding;
    }

    public void setCompress( boolean compress )
    {
        this.doCompress = compress;
    }

    public boolean isCompress()
    {
        return doCompress;
    }

    public boolean isRecompressAddedZips()
    {
        return recompressAddedZips;
    }

    public void setRecompressAddedZips( boolean recompressAddedZips )
    {
        this.recompressAddedZips = recompressAddedZips;
    }

    public void setUpdateMode( boolean update )
    {
        this.doUpdate = update;
        savedDoUpdate = doUpdate;
    }

    public boolean isInUpdateMode()
    {
        return doUpdate;
    }

    /**
     * If true, emulate Sun's jar utility by not adding parent directories;
     * optional, defaults to false.
     * @param f true to emilate sun jar utility
     */
    public void setFilesonly( boolean f )
    {
        doFilesonly = f;
    }

    public boolean isFilesonly()
    {
        return doFilesonly;
    }


    protected void execute()
        throws ArchiverException, IOException
    {
        if ( ! checkForced() )
        {
            return;
        }

        if ( doubleFilePass )
        {
            skipWriting = true;
            createArchiveMain();
            skipWriting = false;
            createArchiveMain();
        }
        else
        {
            createArchiveMain();
        }

        finalizeZipOutputStream( zOut );
    }

    protected void finalizeZipOutputStream( ZipArchiveOutputStream zOut )
        throws IOException, ArchiverException
    {
    }

    private void createArchiveMain()
        throws ArchiverException, IOException
    {
        //noinspection deprecation
        if ( !Archiver.DUPLICATES_SKIP.equals( duplicate ) )
        {
            //noinspection deprecation
            setDuplicateBehavior( duplicate );
        }

        ResourceIterator iter = getResources();
        if ( !iter.hasNext() && !hasVirtualFiles() )
        {
            throw new ArchiverException( "You must set at least one file." );
        }

        zipFile = getDestFile();

        if ( zipFile == null )
        {
            throw new ArchiverException( "You must set the destination " + archiveType + "file." );
        }

        if ( zipFile.exists() && !zipFile.isFile() )
        {
            throw new ArchiverException( zipFile + " isn't a file." );
        }

        if ( zipFile.exists() && !zipFile.canWrite() )
        {
            throw new ArchiverException( zipFile + " is read-only." );
        }

        // Whether or not an actual update is required -
        // we don't need to update if the original file doesn't exist

        addingNewFiles = true;

        if ( doUpdate && !zipFile.exists() )
        {
            doUpdate = false;
            getLogger().debug( "ignoring update attribute as " + archiveType + " doesn't exist." );
        }

        success = false;

        if ( doUpdate )
        {
            renamedFile = FileUtils.createTempFile( "zip", ".tmp", zipFile.getParentFile() );
            renamedFile.deleteOnExit();

            try
            {
                FileUtils.rename( zipFile, renamedFile );
            }
            catch ( SecurityException e )
            {
                getLogger().debug( e.toString() );
                throw new ArchiverException( "Not allowed to rename old file (" + zipFile.getAbsolutePath()
                    + ") to temporary file", e );
            }
            catch ( IOException e )
            {
                getLogger().debug( e.toString() );
                throw new ArchiverException( "Unable to rename old file (" + zipFile.getAbsolutePath()
                    + ") to temporary file", e );
            }
        }

        String action = doUpdate ? "Updating " : "Building ";

        getLogger().info( action + archiveType + ": " + zipFile.getAbsolutePath() );

        if ( !skipWriting )
        {
            FileOutputStream out = new FileOutputStream( zipFile );
            BufferedOutputStream buffered = new BufferedOutputStream( out, 65536 );
            zOut = new ZipArchiveOutputStream( buffered );

            zOut.setEncoding( encoding );
            if ( doCompress )
            {
                zOut.setMethod( ZipArchiveOutputStream.DEFLATED );
            }
            else
            {
                zOut.setMethod( ZipArchiveOutputStream.STORED );
            }
        }
        initZipOutputStream( zOut );

        // Add the new files to the archive.
        addResources( iter, zOut );

        // If we've been successful on an update, delete the
        // temporary file
        if ( doUpdate )
        {
            if ( !renamedFile.delete() )
            {
                getLogger().warn( "Warning: unable to delete temporary file " + renamedFile.getName() );
            }
        }
        success = true;
    }

    protected Map<String, Long> getZipEntryNames( File file )
        throws IOException
    {
        if ( !file.exists()  ||  !doUpdate )
        {
            //noinspection unchecked
            return Collections.EMPTY_MAP;
        }
        final Map<String, Long> entries = new HashMap<String, Long>();
        final org.apache.commons.compress.archivers.zip.ZipFile zipFile = new org.apache.commons.compress.archivers.zip.ZipFile( file );
        for ( Enumeration en = zipFile.getEntries();  en.hasMoreElements();  )
        {
            ZipArchiveEntry ze = (ZipArchiveEntry) en.nextElement();
            entries.put( ze.getName(), ze.getLastModifiedDate().getTime());
        }
        return entries;
    }

    protected static boolean isFileAdded(ArchiveEntry entry, Map entries)
    {
        return !entries.containsKey( entry.getName() );
    }

    protected static boolean isFileUpdated(ArchiveEntry entry, Map entries)
    {
        Long l = (Long) entries.get( entry.getName() );
        return l != null && (l == -1 || !ResourceUtils.isUptodate(entry.getResource(), l));
    }

    /**
     * Add the given resources.
     *
     * @param resources the resources to add
     * @param zOut      the stream to write to
     */
    @SuppressWarnings({"JavaDoc"})
    protected final void addResources( ResourceIterator resources, ZipArchiveOutputStream zOut )
        throws IOException, ArchiverException
    {
        File base = null;

        while ( resources.hasNext() )
        {
            ArchiveEntry entry = resources.next();
            String name = entry.getName();
            name = name.replace( File.separatorChar, '/' );

            if ( "".equals( name ) )
            {
                continue;
            }

            if ( entry.getResource().isDirectory() && !name.endsWith( "/" ) )
            {
                name = name + "/";
            }

            addParentDirs( base, name, zOut, "" );

            if ( entry.getResource().isFile() )
            {
                zipFile( entry, zOut, name );
            }
            else
            {
                zipDir( entry.getResource(), zOut, name, entry.getMode() );
            }
        }
    }

    /**
     * Ensure all parent dirs of a given entry have been added.
	 *
	 * This method is computed in terms of the potentially remapped entry (that may be disconnected from the file system)
	 * we do not *relly* know the entry's connection to the file system so establishing the attributes of the parents can
	 * be impossible and is not really supported.
	 *
     */
    @SuppressWarnings({"JavaDoc"})
    protected final void addParentDirs( File baseDir, String entry, ZipArchiveOutputStream zOut, String prefix )
        throws IOException
    {
        if ( !doFilesonly && getIncludeEmptyDirs() )
        {
            Stack<String> directories = new Stack<String>();

            // Don't include the last entry itself if it's
            // a dir; it will be added on its own.
            int slashPos = entry.length() - ( entry.endsWith( "/" ) ? 1 : 0 );

            while ( ( slashPos = entry.lastIndexOf( '/', slashPos - 1 ) ) != -1 )
            {
                String dir = entry.substring( 0, slashPos + 1 );

                if ( addedDirs.contains( prefix + dir ) )
                {
                    break;
                }

                directories.push( dir );
            }

            while ( !directories.isEmpty() )
            {
                String dir = directories.pop();
                File f;
                if ( baseDir != null )
                {
                    f = new File( baseDir, dir );
                }
                else
                {
                    f = new File( dir );
                }
				// the
				// At this point we could do something like read the atr
				final PlexusIoResource res = new AnonymousResource( f);
                zipDir( res, zOut, prefix + dir, getRawDefaultDirectoryMode() );
            }
        }
    }

    private void readWithZipStats(InputStream in, byte[] header, ZipArchiveEntry ze, ByteArrayOutputStream bos) throws IOException {
        byte[] buffer = new byte[8 * 1024];

        CRC32 cal2 = new CRC32();

        long size = 0;

        for (byte aHeader : header) {
            cal2.update(aHeader);
            size++;
        }

        int count = 0;
        do
        {
            size += count;
            cal2.update( buffer, 0, count );
            if (bos != null)
            {
                bos.write( buffer, 0, count );
            }
            count = in.read( buffer, 0, buffer.length );
        }
        while ( count != -1 );
        ze.setSize(size);
        ze.setCrc(cal2.getValue());
    }

    public static long copy( final InputStream input,
                             final OutputStream output,
                             final int bufferSize )
            throws IOException
    {
        final byte[] buffer = new byte[bufferSize];
        long size = 0;
        int n;
        while ( -1 != ( n = input.read( buffer ) ) )
        {
            size += n;
            output.write( buffer, 0, n );
        }
        return size;
    }

    /**
     * Adds a new entry to the archive, takes care of duplicates as well.
     *
     * @param in           the stream to read data for the entry from.
     * @param zOut         the stream to write to.
     * @param vPath        the name this entry shall have in the archive.
     * @param lastModified last modification time for the entry.
     * @param fromArchive  the original archive we are copying this
     * @param symlinkDestination
     */
    @SuppressWarnings({"JavaDoc"})
    protected void zipFile( InputStream in, ZipArchiveOutputStream zOut, String vPath, long lastModified, File fromArchive,
                            int mode, String symlinkDestination )
        throws IOException, ArchiverException
    {
        getLogger().debug( "adding entry " + vPath );

        entries.put( vPath, vPath );

        if ( !skipWriting )
        {
            ZipArchiveEntry ze = new ZipArchiveEntry( vPath );
            setTime(ze, lastModified);

            byte[] header = new byte[4];
            int read = in.read(header);

            boolean compressThis = doCompress;
            if (!recompressAddedZips && isZipHeader(header)){
                compressThis = false;
            }

            ze.setMethod( compressThis ? ZipArchiveEntry.DEFLATED : ZipArchiveEntry.STORED );
            ze.setUnixMode( UnixStat.FILE_FLAG | mode );
            /*
             * ZipOutputStream.putNextEntry expects the ZipEntry to
             * know its size and the CRC sum before you start writing
             * the data when using STORED mode - unless it is seekable.
             *
             * This forces us to process the data twice.
             */


            if (ze.isUnixSymlink()){
                zOut.putArchiveEntry( ze );
                final byte[] bytes = symlinkDestination.getBytes(); // encoding ??
                zOut.write( bytes, 0, bytes.length);
            } else if (zOut.isSeekable() || compressThis) {
                zOut.putArchiveEntry( ze );
                if (read > 0) zOut.write(header, 0, read);
                IOUtil.copy( in, zOut, 8 * 1024);
            } else {
                if (in.markSupported())
                {
                    in.mark( Integer.MAX_VALUE );
                    readWithZipStats(in, header, ze, null);
                    in.reset();
                    zOut.putArchiveEntry( ze);
                    if (read > 0) zOut.write(header, 0, read);
                    IOUtil.copy(in, zOut, 8 * 1024);
                }
                else
                {
                    // Store data into a byte[]
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(128 * 1024);
                    readWithZipStats(in, header, ze, bos);
                    zOut.putArchiveEntry(ze);
                    if (read > 0) zOut.write(header, 0, read);
                    bos.writeTo( zOut);
                }
            }

            zOut.closeArchiveEntry();
        }
    }

    private boolean isZipHeader(byte[] header) {
        return header[0] == 0x50 && header[1] == 0x4b && header[2] == 03 && header[3] == 04;
    }

    /**
     * Method that gets called when adding from java.io.File instances.
     * <p/>
     * <p>This implementation delegates to the six-arg version.</p>
     *
     * @param entry the file to add to the archive
     * @param zOut  the stream to write to
     * @param vPath the name this entry shall have in the archive
     */
    @SuppressWarnings({"JavaDoc"})
    protected void zipFile( ArchiveEntry entry, ZipArchiveOutputStream zOut, String vPath )
        throws IOException, ArchiverException
    {
		final PlexusIoResource resource = entry.getResource();
		if ( ResourceUtils.isSame(resource, getDestFile() ) )
        {
            throw new ArchiverException( "A zip file cannot include itself" );
        }

        final boolean b = entry.getResource() instanceof PlexusIoSymlink;
        String symlinkTarget = b ? ((PlexusIoSymlink)entry.getResource()).getSymlinkDestination() : null;
        InputStream in = entry.getInputStream();
        try
        {
			zipFile( in, zOut, vPath, resource.getLastModified(), null, entry.getMode(), symlinkTarget );
        }
        catch ( IOException e )
        {
            throw new ArchiverException( "IOException when zipping " + entry.getName() + ": " + e.getMessage(), e );
        }
        finally
        {
            IOUtil.close( in );
        }
    }

	private void setTime(java.util.zip.ZipEntry zipEntry, long lastModified){
		zipEntry.setTime( lastModified + ( isJava7OrLower ? 1999 : 0 ) );

		/*   Consider adding extended file stamp support.....

		X5455_ExtendedTimestamp ts =  new X5455_ExtendedTimestamp();
		ts.setModifyJavaTime(new Date(lastModified));
		if (zipEntry.getExtra() != null){
			// Uh-oh. What do we do now.
			throw new IllegalStateException("DIdnt expect to see xtradata here ?");

		}  else {
			zipEntry.setExtra(ts.getLocalFileDataData());
		}
        */
	}

    protected void zipDir( PlexusIoResource dir, ZipArchiveOutputStream zOut, String vPath, int mode )
        throws IOException
    {
        if ( addedDirs.get( vPath ) != null )
        {
            // don't add directories we've already added.
            // no warning if we try, it is harmless in and of itself
            return;
        }

        getLogger().debug( "adding directory " + vPath );
        addedDirs.put( vPath, vPath );

        if ( !skipWriting )
        {
            ZipArchiveEntry ze = new ZipArchiveEntry( vPath );

            /*
             * ZipOutputStream.putNextEntry expects the ZipEntry to
             * know its size and the CRC sum before you start writing
             * the data when using STORED mode - unless it is seekable.
             *
             * This forces us to process the data twice.
             */


            final boolean isSymlink = dir instanceof PlexusIoSymlink;
            if (isSymlink) mode = UnixStat.LINK_FLAG | mode;


            if ( dir != null && dir.isExisting() )
            {
                setTime( ze, dir.getLastModified());
			}
            else
            {
                // ZIPs store time with a granularity of 2 seconds, round up
                setTime( ze, System.currentTimeMillis() );
            }
            if (!isSymlink){
                ze.setSize( 0 );
                ze.setMethod( ZipArchiveEntry.STORED );
                // This is faintly ridiculous:
                ze.setCrc( EMPTY_CRC );
            }
            ze.setUnixMode( mode );

            zOut.putArchiveEntry( ze );

            if ( isSymlink )
            {
                String symlinkDestination = ( (PlexusIoSymlink) dir ).getSymlinkDestination();
                ZipEncoding enc = ZipEncodingHelper.getZipEncoding( zOut.getEncoding() );
                final byte[] bytes = enc.encode( symlinkDestination ).array();
                zOut.write( bytes, 0, bytes.length );

            }

            zOut.closeArchiveEntry();
        }
    }

    /**
     * Create an empty zip file
     *
     * @param zipFile The file
     * @return true for historic reasons
     */
    @SuppressWarnings({"JavaDoc"})
    protected boolean createEmptyZip( File zipFile )
        throws ArchiverException
    {
        // In this case using java.util.zip will not work
        // because it does not permit a zero-entry archive.
        // Must create it manually.
        getLogger().info( "Note: creating empty " + archiveType + " archive " + zipFile );
        OutputStream os = null;
        try
        {
            os = new FileOutputStream( zipFile );
            // Cf. PKZIP specification.
            byte[] empty = new byte[22];
            empty[0] = 80; // P
            empty[1] = 75; // K
            empty[2] = 5;
            empty[3] = 6;
            // remainder zeros
            os.write( empty );
        }
        catch ( IOException ioe )
        {
            throw new ArchiverException( "Could not create empty ZIP archive " + "(" + ioe.getMessage() + ")", ioe );
        }
        finally
        {
            IOUtil.close( os );
        }
        return true;
    }

    /**
     * Do any clean up necessary to allow this instance to be used again.
     * <p/>
     * <p>When we get here, the Zip file has been closed and all we
     * need to do is to reset some globals.</p>
     * <p/>
     * <p>This method will only reset globals that have been changed
     * during execute(), it will not alter the attributes or nested
     * child elements.  If you want to reset the instance so that you
     * can later zip a completely different set of files, you must use
     * the reset method.</p>
     *
     * @see #reset
     */
    protected void cleanUp()
    {
        super.cleanUp();
        addedDirs.clear();
        entries.clear();
        addingNewFiles = false;
        doUpdate = savedDoUpdate;
        success = false;
        zOut = null;
        renamedFile = null;
        zipFile = null;
    }

    /**
     * Makes this instance reset all attributes to their default
     * values and forget all children.
     *
     * @see #cleanUp
     */
    public void reset()
    {
        setDestFile( null );
//        duplicate = "add";
        archiveType = "zip";
        doCompress = true;
        doUpdate = false;
        doFilesonly = false;
        encoding = null;
    }

    /**
     * method for subclasses to override
     */
    protected void initZipOutputStream( ZipArchiveOutputStream zOut )
        throws IOException, ArchiverException
    {
    }

    /**
     * method for subclasses to override
     */
    public boolean isSupportingForced()
    {
        return true;
    }

    protected boolean revert( StringBuffer messageBuffer )
    {
        int initLength = messageBuffer.length();

        // delete a bogus ZIP file (but only if it's not the original one)
        if ( ( !doUpdate || renamedFile != null ) && !zipFile.delete() )
        {
            messageBuffer.append( " (and the archive is probably corrupt but I could not delete it)" );
        }

        if ( doUpdate && renamedFile != null )
        {
            try
            {
                FileUtils.rename( renamedFile, zipFile );
            }
            catch ( IOException e )
            {
                messageBuffer.append( " (and I couldn't rename the temporary file " );
                messageBuffer.append( renamedFile.getName() );
                messageBuffer.append( " back)" );
            }
        }

        return messageBuffer.length() == initLength;
    }

    protected void close()
        throws IOException
    {
        // Close the output stream.
        try
        {
            if ( zOut != null )
            {
                zOut.close();
            }
        }
        catch ( IOException ex )
        {
            // If we're in this finally clause because of an
            // exception, we don't really care if there's an
            // exception when closing the stream. E.g. if it
            // throws "ZIP file must have at least one entry",
            // because an exception happened before we added
            // any files, then we must swallow this
            // exception. Otherwise, the error that's reported
            // will be the close() error, which is not the
            // real cause of the problem.
            if ( success )
            {
                throw ex;
            }
        }
    }

    protected String getArchiveType()
    {
        return archiveType;
    }

}
