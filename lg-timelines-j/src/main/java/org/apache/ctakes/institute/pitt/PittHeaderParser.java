package org.apache.ctakes.institute.pitt;

import org.apache.ctakes.core.util.CalendarUtil;
import org.apache.ctakes.core.util.doc.SourceMetadataUtil;
import org.apache.ctakes.core.util.regex.TimeoutMatcher;
import org.apache.ctakes.typesystem.type.structured.SourceData;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parses document metadata from a University of Pittsburgh de-identified doc with standard header.
 * @author SPF , chip-nlp
 * @since {3/16/2026}
 */
public class PittHeaderParser extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( "PittHeaderParser" );

   static private final Pattern DATE_PATTERN = Pattern.compile( "Principal Date[.]+(\\d+)( \\d+)?" );
   static private final Pattern DOC_TYPE_PATTERN = Pattern.compile( "Record Type[.]+([a-zA-Z ]+)" );

   static private final String DATE_TIME_FORMAT = "yyyyMMddkkmm";
   static private final String DATE_FORMAT = "yyyyMMdd";
   static private final String TIME_FORMAT = "kkmm";
   static private final String CAS_DATE_TIME_FORMAT = "MMddyyyykkmmss";

   static private final DateTimeFormatter DATE_TIME_PARSER = DateTimeFormatter.ofPattern( DATE_TIME_FORMAT );
//
   static private final DateFormat DATE_TIME_FORMATTER = new SimpleDateFormat( DATE_TIME_FORMAT );
   static private final DateFormat CAS_DATE_TIME_FORMATTER = new SimpleDateFormat( CAS_DATE_TIME_FORMAT );


   static private final String INSTITUTION = "University of Pittsburgh";

   /**
    * {@inheritDoc}
    */
   @Override
   public void initialize( UimaContext context ) throws ResourceInitializationException {
      super.initialize( context );
      CalendarUtil.addDateTimeFormat( DATE_TIME_FORMAT );
      CalendarUtil.addDateFormat( DATE_FORMAT );
      CalendarUtil.addTimeFormat( TIME_FORMAT );
   }


   /**
    * Grabs the document time from the header
    * {@inheritDoc}
    */
   @Override
   public void process( final JCas jcas ) throws AnalysisEngineProcessException {
      SourceMetadataUtil.getOrCreateSourceData( jcas ).setSourceInstitution( INSTITUTION );
      LOGGER.info( "Parsing the Document Header ..." );
      boolean haveDocTime = false;
      boolean haveDocType = false;
      final Collection<Segment> sections = JCasUtil.select( jcas, Segment.class );
      for ( Segment section : sections ) {
         final String text = section.getCoveredText();
         if  ( !haveDocTime ) {
            final String docTimeText = getDocTimeText( text );
            if ( !docTimeText.isBlank() ) {
               final Calendar docTime = normalizeTimeText( docTimeText );
               if ( docTime != null && !docTime.equals( CalendarUtil.NULL_CALENDAR ) ) {
                  setDocTime( jcas, docTime );
                  haveDocTime = true;
               }
            }
         }
         if ( !haveDocType ) {
            final String docTypeText = getDocTypeText( text );
            if ( !docTypeText.isBlank() ) {
               setDocType( jcas, docTypeText );
               haveDocType = true;
            }
         }
         if ( haveDocTime && haveDocType ) {
            break;
         }
      }
   }

   /**
    *
    * @param text line of text from the University of Pittsburgh Header.
    * @return a date and time from the text.
    */
   private String getDocTimeText( final String text ) {
      try ( final TimeoutMatcher dateMatcher = new TimeoutMatcher( DATE_PATTERN, text ) ) {
         Matcher safeMatch = dateMatcher.nextMatch();
         if ( safeMatch == null ) {
            return "";
         }
         final int begin = safeMatch.start( 1 );
         // The header may not have a time.
         int end = safeMatch.end( 1 );
         if ( safeMatch.groupCount() == 2 ) {
            end = (safeMatch.end( 2 ) > begin)
                  ? safeMatch.end( 2 )
                  : safeMatch.end( 1 );
         }
         final String docTimeText = text.substring( begin, end ).replace( " ", "" );
         if ( docTimeText.length() == 8 ) {
            return docTimeText + "1200";
         }
         return docTimeText;
      } catch ( IllegalArgumentException iaE ) {
         LOGGER.warn( iaE.getMessage() );
      }
      return "";
   }

   /**
    *
    * @param docTimeText a date and time from the text.
    * @return a Calendar object for normalization.
    */
   private Calendar normalizeTimeText( final String docTimeText ) {
      try {
         final Date date = DATE_TIME_FORMATTER.parse( docTimeText );
         final Calendar calendar = Calendar.getInstance();
         calendar.setTime( date );
         return calendar;
      } catch ( ParseException pE ) {
         return CalendarUtil.NULL_CALENDAR;
      }
   }

   /**
    *
    * @param jCas ye olde ...
    * @param docTime a normalized form of docTime to store in the cas.
    */
   private void setDocTime( final JCas jCas, final Calendar docTime ) {
      final String casTimeText = CAS_DATE_TIME_FORMATTER.format( docTime.getTime() );
      LOGGER.info( "Setting cas date date to {}", casTimeText );
      SourceMetadataUtil.setDocCreationDate(jCas, docTime );
      final SourceData sourceData = SourceMetadataUtil.getOrCreateSourceData( jCas );
      sourceData.setSourceOriginalDate( casTimeText );
      sourceData.setSourceRevisionDate( casTimeText );
   }


   /**
    *
    * @param text a line from the University of Pittsburgh Header.
    * @return the normalized document type.
    */
   private String getDocTypeText( final String text ) {
      try ( final TimeoutMatcher typeMatcher = new TimeoutMatcher( DOC_TYPE_PATTERN, text ) ) {
         Matcher safeMatch = typeMatcher.nextMatch();
         if ( safeMatch == null ) {
            return "";
         }
         final int begin = safeMatch.start( 1 );
         final int end = safeMatch.end( 1 );
         return text.substring( begin, end );
      } catch ( IllegalArgumentException iaE ) {
         LOGGER.warn( iaE.getMessage() );
      }
      return "";
   }

   /**
    *
    * @param jCas ye olde ...
    * @param docType the normalized document type. to store in the cas.
    */
   private void setDocType( final JCas jCas, final String docType ) {
      SourceMetadataUtil.getOrCreateSourceData( jCas ).setNoteTypeCode( docType );
   }


}
