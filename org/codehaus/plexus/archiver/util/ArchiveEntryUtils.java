package org.codehaus.plexus.archiver.util;

import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.attributes.Java7AttributeUtils;
import org.codehaus.plexus.components.io.attributes.Java7Reflector;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

@SuppressWarnings("JavaDoc")
public final class ArchiveEntryUtils
{

    public static boolean jvmFilePermAvailable;

    static
    {
        try
        {
            jvmFilePermAvailable = File.class.getMethod( "setReadable", Boolean.TYPE) != null;
        }
        catch ( final Exception e )
        {
            // ignore exception log this ?
        }
    }

    private ArchiveEntryUtils()
    {
        // no op
    }

    /**
     * @since 1.1
     * @param file
     * @param mode
     * @param logger
     * @param useJvmChmod
     *            will use jvm file permissions <b>not available for group level</b>
     * @throws ArchiverException
     */
    public static void chmod( final File file, final int mode, final Logger logger, boolean useJvmChmod )
        throws ArchiverException
    {
        if ( !Os.isFamily( Os.FAMILY_UNIX ) )
        {
            return;
        }

		if (Java7Reflector.isAtLeastJava7())
		{
			try
			{
				Java7AttributeUtils.chmod(file, mode);
				return;
			} catch (IOException e)
			{
				throw new ArchiverException("Failed setting file attributes with java7+", e);
			}
		}

        final String m = Integer.toOctalString( mode & 0xfff );

        if ( useJvmChmod && !jvmFilePermAvailable )
        {
            useJvmChmod = false;
        }

        if (useJvmChmod)
        {
            applyPermissionsWithJvm( file, m, logger );
            return;
        }

        try
        {
            final Commandline commandline = new Commandline();

            commandline.setWorkingDirectory( file.getParentFile().getAbsolutePath() );

            if ( logger.isDebugEnabled() )
            {
                logger.debug( file + ": mode " + Integer.toOctalString( mode ) + ", chmod " + m );
            }

            commandline.setExecutable( "chmod" );

            commandline.createArg().setValue( m );

            final String path = file.getAbsolutePath();

            commandline.createArg().setValue( path );

            // commenting this debug statement, since it can produce VERY verbose output...
            // this method is called often during archive creation.
            // logger.debug( "Executing:\n\n" + commandline.toString() + "\n\n" );

            final CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

            final CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();

            final int exitCode = CommandLineUtils.executeCommandLine( commandline, stderr, stdout );

            if ( exitCode != 0 )
            {
                logger.warn( "-------------------------------" );
                logger.warn( "Standard error:" );
                logger.warn( "-------------------------------" );
                logger.warn( stderr.getOutput() );
                logger.warn( "-------------------------------" );
                logger.warn( "Standard output:" );
                logger.warn( "-------------------------------" );
                logger.warn( stdout.getOutput() );
                logger.warn( "-------------------------------" );

                throw new ArchiverException( "chmod exit code was: " + exitCode );
            }
        }
        catch ( final CommandLineException e )
        {
            throw new ArchiverException( "Error while executing chmod.", e );
        }

    }

    /**
     * <b>jvm chmod will be used only if System property <code>useJvmChmod</code> set to true</b>
     * 
     * @param file
     * @param mode
     * @param logger
     * @throws ArchiverException
     */
    public static void chmod( final File file, final int mode, final Logger logger )
        throws ArchiverException
    {
        chmod( file, mode, logger, Boolean.getBoolean( "useJvmChmod" ) && jvmFilePermAvailable );
    }

    private static void applyPermissionsWithJvm( final File file, final String mode, final Logger logger )
        throws ArchiverException
    {
        final FilePermission filePermission = FilePermissionUtils.getFilePermissionFromMode( mode, logger );

        Method method;
        try
        {
            method = File.class.getMethod( "setReadable", Boolean.TYPE, Boolean.TYPE);

            method.invoke( file,
					filePermission.isReadable(),
					filePermission.isOwnerOnlyReadable());

            method = File.class.getMethod( "setExecutable", Boolean.TYPE, Boolean.TYPE);
            method.invoke( file,
					filePermission.isExecutable(),
					filePermission.isOwnerOnlyExecutable());

            method = File.class.getMethod( "setWritable", Boolean.TYPE, Boolean.TYPE);
            method.invoke( file,
					filePermission.isWritable(),
					filePermission.isOwnerOnlyWritable());
        }
        catch ( final Exception e )
        {
            logger.error( "error calling dynamically file permissons with jvm " + e.getMessage(), e );
            throw new ArchiverException( "error calling dynamically file permissons with jvm " + e.getMessage(), e );
        }
    }

}
