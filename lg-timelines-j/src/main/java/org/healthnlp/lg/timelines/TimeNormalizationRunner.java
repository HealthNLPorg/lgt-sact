package org.healthnlp.lg.timelines;

import org.apache.ctakes.core.util.StringUtil;
import org.apache.ctakes.core.util.annotation.OntologyConceptUtil;
import org.apache.ctakes.core.util.annotation.SemanticGroup;
import org.apache.ctakes.core.util.doc.SourceMetadataUtil;
import org.apache.ctakes.typesystem.type.refsem.Date;
import org.apache.ctakes.typesystem.type.refsem.Duration;
import org.apache.ctakes.typesystem.type.refsem.Frequency;
import org.apache.ctakes.typesystem.type.refsem.Time;
import org.apache.ctakes.typesystem.type.textsem.DurationModifier;
import org.apache.ctakes.typesystem.type.textsem.FrequencyModifier;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.clulab.timenorm.scfg.TimeSpan;
import org.healthnlp.timenorm.TimexNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Annotation Engine that normalizes all Time concepts and TimeMention mentions in a cas.
 * @author SPF , chip-nlp
 * @since {2/12/2026}
 */
public class TimeNormalizationRunner extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( "TimeNormalizationRunner" );


   /**
    * Iterates over TimeMentions in the cas, normalizing each using TimeNorm.
    * The "time class" of the mention is set to "TimeNorm [normal]" where normal is the exact value obtained using TimeNorm.
    * If TimeNorm does not provide a normalized value, the "time class" is set to "TimeNorm FAILED_NORMALIZATION"
    *
    * @param jCas ye olde ...
    * @throws AnalysisEngineProcessException if anything goes wrong
    */
   @Override
   public void process( final JCas jCas) throws AnalysisEngineProcessException {
      // Get any Time Elements - which can be used to store concepts indirectly associated with the text.
      final Collection<Time> times = JCasUtil.select( jCas, Time.class );
      final Collection<IdentifiedAnnotation> timeMentions
            = OntologyConceptUtil.getAnnotationsBySemanticGroup( jCas, SemanticGroup.TIME );
      if ( times.isEmpty() && timeMentions.isEmpty() ) {
         return;
      }
      final Date docDate = SourceMetadataUtil.getDocDate( jCas );
      final Time docTime = SourceMetadataUtil.getDocTime( jCas );
      final TimeSpan docTimeSpan = getTimeSpan( docDate );
      // For other (future) projects, do not use simple format.  The complex normalization consists of values like
      // TimeSpan(2026-06-15T00:00Z,2026-06-16T00:00Z,Period(Map(Days -> 1),Exact),Exact)
      try ( TimexNormalizer normalizer = new TimexNormalizer() ) {
         for ( Time time : times ) {
            if ( time.equals( docTime ) ) {
               continue;
            }
            // This may be text that is already a isoText form or text that is not.
            // Go with the assumption that it is not already a isoText form,
            // and if TimeNorm cannot re-normalize it then nothing will happen.
            // Hopefully, if TimeNorm gets text that is already in a isoText form it will just return it.
            final String normalizedForm = time.getNormalizedForm();
            final String isoText = callTimeNorm( normalizer, normalizedForm, docTimeSpan );
            if ( isoText.isEmpty() ) {
               continue;
            }
            normalizeTime( docTime, time, isoText );
         }
         for ( IdentifiedAnnotation timex : timeMentions ) {
            final TimeMention timeMention = (TimeMention)timex;
            final String normalized = callTimeNorm( normalizer, timeMention.getCoveredText(), docTimeSpan );
            if ( normalized.isEmpty() ) {
               timeMention.setTimeClass( TimeNormalUtil.FAILED_NORMALIZATION );
               continue;
            }
            normalizeTimeMention( jCas, docDate, docTime, timeMention, normalized );
         }
      }
   }

   /**
    *
    * @param normalizer TimexNormalizer that delegates to TimeNorm.
    * @param text text containing some temporal expression.
    * @param docTimeSpan time creation time.
    * @return normalized text representation of time, date, or duration.  Empty text if normalization could not be done.
    */
   static private String callTimeNorm( final TimexNormalizer normalizer, final String text, final TimeSpan docTimeSpan ) {
      if ( text == null || text.isEmpty() ) {
         return "";
      }
      try {
         final String normalized = normalizer.normalize( text, docTimeSpan );
         if ( normalized == null || normalized.isEmpty() ) {
            return "";
         }
         return normalized;
      } catch ( IllegalArgumentException iaE ) {
         LOGGER.warn( "Could not normalize temporal expression {}", text );
         return "";
      }
   }

   /**
    *    // YYY-MM-DDTHH:mm:SSZ
    * Turn text like "2026-02-01" (Jan 1 2026), "2026-01" (Jan 2026), and "2026" (2026) to a ctakes Date.
    * There are special cases for Weeks like "2026-W01" (the first week of 2026).
    * Note that this turns what should/could be a span (whole day, month, year, week) into a point in time.
    * Strings that represent Date and Time will be split.  e.g. 2026-02-13T05:15:30
    * @param jCas -
    * @param docDate the document creation date (aka DocTime).
    * @param docTime the document creation time (aka DocTime).
    * @param timeMention -
    * @param isoText isoText date representation from TimeNorm.
    * @return ctakes TimeMention with Date and Time classes holding Year, Month, Day, Hour, Minute and Second.
    */
   static private TimeMention normalizeTimeMention( final JCas jCas, final Date docDate, final Time docTime,
                                                    final TimeMention timeMention, final String isoText ) {
      // type Time may or may not hold a isoText time, but it always holds the isoText text representation.
      final Time time = timeMention.getTime();
      if ( time != null ) {
         normalizeTime( docTime, time, isoText );
      } else {
         timeMention.setTime( createNormalizedTime( jCas, docTime, isoText ) );
      }
      final String timeClass = getTimeClass( isoText );
      timeMention.setTimeClass( timeClass );
      if ( timeClass.equals( TimeNormalUtil.INSTANT ) ) {
         final Date date = timeMention.getDate();
         if ( date != null ) {
            normalizeDate( docDate, date, isoText );
         } else {
            timeMention.setDate( createNormalizedDate( jCas, docDate, isoText ) );
         }
      } else if ( timeClass.equals( TimeNormalUtil.DURATION ) ) {
         final Duration duration = timeMention.getDuration();
         if ( duration != null ) {
            normalizeDuration( duration, isoText );
         } else {
            timeMention.setDuration( createNormalizedDuration( jCas, isoText ) );
         }
         // We can try to use this later when ctakes does something with duration modifiers.
//                  createDurationModifier( jCas, timeMention.getBegin(), timeMention.getEnd(), isoText );
      }
      return timeMention;
   }

   /**
    *    // YYY-MM-DDTHH:mm:SSZ
    * Turn text like "2026-02-01" (Jan 1 2026), "2026-01" (Jan 2026), and "2026" (2026) to a ctakes Date.
    * There are special cases for Weeks like "2026-W01" (the first week of 2026).
    * Note that this turns what should/could be a span (whole day, month, year, week) into a point in time.
    * Strings that represent Date and Time will be split.  e.g. 2026-02-13T05:15:30
    * @param jCas ye olde ...
    * @param docDate the document creation date (aka DocTime).
    * @param isoText isoText date representation from TimeNorm.
    * @return ctakes Date class that holds the isoText Year, Month, and Day.
    */
   static private Date createNormalizedDate( final JCas jCas, final Date docDate, final String isoText ) {
      final int tIndex = isoText.indexOf( 'T' );
      if ( tIndex > 0 && !isoText.equalsIgnoreCase( TimeNormalUtil.PRESENT_REF ) ) {
         // Strip time normalization
         return createNormalizedDate( jCas, docDate, isoText.substring( 0, tIndex ) );
      }
      final Date date = new Date( jCas );
      return normalizeDate( docDate, date, isoText );
   }

   /**
    *
    * @param docDate the document creation date (aka DocTime).
    * @param date some date in the cas to which the attributes of docDate will be copied.
    * @return a copy of date with its attributes set to the values from docDate.
    */
   static private Date copyDocDate( final Date docDate, final Date date ) {
      date.setYear( docDate.getYear() );
      date.setYearValue( docDate.getYearValue() );
      date.setMonth( docDate.getMonth() );
      date.setMonthValue( docDate.getMonthValue() );
      date.setDay( docDate.getDay() );
      date.setDayValue( docDate.getDayValue() );
      return date;
   }

   /**
    *    // YYY-MM-DDTHH:mm:SSZ
    * Turn text like "2026-02-01" (Jan 1 2026), "2026-01" (Jan 2026), and "2026" (2026) to a ctakes Date.
    * There are special cases for Weeks like "2026-W01" (the first week of 2026).
    * Note that this turns what should/could be a span (whole day, month, year, week) into a point in time.
    * Strings that represent Date and Time will be split.  e.g. 2026-02-13T05:15:30
    * @param docDate the document creation date (aka DocTime).
    * @param date Dime, with or without normalization.  Any existing normalization will be overwritten.-
    * @param isoText isoText date representation from TimeNorm.
    * @return ctakes Date class that holds the isoText Year, Month, and Day.
    */
   static private Date normalizeDate( final Date docDate, final Date date, final String isoText ) {
      date.setTextRepresentation( isoText );
      if ( isoText.equalsIgnoreCase( TimeNormalUtil.PRESENT_REF ) ) {
         return copyDocDate( docDate, date );
      }
      final String[] splits = StringUtil.fastSplit( isoText, '-' );
      date.setYear( splits[ 0 ] );
      date.setYearValue( getIntValue( splits[0] ) );
      if ( splits.length == 1 ) {
         date.setMonthValue( 0 );
         date.setDayValue( 0 );
         return date;
      }
      if ( splits[1].charAt( 0 ) == 'W' ) {
         // Special case for week specification like "2026-W01" (first week in 2026).
         final int weeks = getIntValue( splits[1].substring( 1 ) );
         if ( weeks < 0 ) {
            date.setMonthValue( 0 );
            date.setDayValue( 0 );
            return date;
         }
         final int year = getIntValue( splits[0] );
         final LocalDate weekDate = LocalDate.ofYearDay( year, 1 )
                                             .plusWeeks( weeks-1 );
         date.setMonth( splits[1] );
         date.setDay( splits[1] );
         date.setMonthValue( weekDate.getMonthValue() );
         date.setDayValue( weekDate.getDayOfMonth() );
         return date;
      }
      date.setMonth( splits[1] );
      date.setMonthValue( getIntValue( splits[1] ) );
      if ( splits.length > 2 ) {
         date.setDay( splits[2] );
         date.setDayValue( getIntValue( splits[2] ) );
      } else {
         date.setDayValue( 0 );
      }
      return date;
   }

   /**
    * @param jCas -
    * @param docTime the document creation time (aka DocTime).
    * @param isoText isoText date representation from TimeNorm.
    * @return a ctakes Time class that holds the given timenorm text and isoText hour, minute, second.
    */
   static private Time createNormalizedTime( final JCas jCas, final Time docTime, final String isoText ) {
      if ( isoText.charAt( isoText.length()-1 ) == 'Z' ) {
         return createNormalizedTime( jCas, docTime, isoText.substring( 0, isoText.length()-1 ) );
      }
      final Time time = new Time( jCas );
      return normalizeTime( docTime, time, isoText );
   }

   static private Time copyDocTime( final Time docTime, final Time time ) {
      time.setHour( docTime.getHour() );
      time.setHourValue( docTime.getHourValue() );
      time.setMinute( docTime.getMinute() );
      time.setMinuteValue( docTime.getMinuteValue() );
      time.setSecond( docTime.getSecond() );
      time.setSecondValue( docTime.getSecondValue() );
      return time;
   }


   /**
    * @param docTime the document creation time (aka DocTime).
    * @param time Time, with or without normalization.  Any existing normalization will be overwritten.
    * @param isoText isoText date representation from TimeNorm.
    * @return a ctakes Time class that holds the given timenorm text and isoText hour, minute, second.
    */
   static private Time normalizeTime( final Time docTime, final Time time, final String isoText ) {
      // Legacy ctakes used type Time as holder for entire isoText text and nothing else.
      time.setTextRepresentation( isoText );
      time.setNormalizedForm( isoText );
      if ( isoText.equalsIgnoreCase( TimeNormalUtil.PRESENT_REF ) ) {
         return copyDocTime( docTime, time );
      }
      // YYY-MM-DDTHH:mm:SSZ
      int t = isoText.indexOf( 'T' );
      if ( t < 0 || t == isoText.length()-1 ) {
         return time;
      }
      final String[] splits = StringUtil.fastSplit( isoText.substring( t+1 ), ':' );
      time.setHour( splits[0] );
      time.setHourValue( getIntValue( splits[0] ) );
      if ( splits.length > 1 ) {
         time.setMinute( splits[1] );
         time.setMinuteValue( getIntValue( splits[1] ) );
      } else {
         time.setMinuteValue( 0 );
      }
      if ( splits.length > 2 ) {
         time.setSecond( splits[2] );
         time.setSecondValue( getIntValue( splits[2] ) );
      } else {
         time.setSecondValue( 0 );
      }
      return time;
   }

   /**
    *
    * @param normalized some normalized time, usually ISO.
    * @return the class of normalization, DURATION or INSTANT.
    */
   static private String getTimeClass( final String normalized ) {
      if ( normalized.charAt( 0 ) == 'P' && !normalized.equalsIgnoreCase( TimeNormalUtil.PRESENT_REF ) ) {
         return TimeNormalUtil.DURATION;
      }
      return TimeNormalUtil.INSTANT;
   }

   /**
    * Durations from TimeNorm are represented with as a Period of time, e.g. "PT1H".
    * @param jCas -
    * @param normalized TimeNorm text representation of normalized duration.
    * @return a ctakes representation of the duration.
    */
   static private Duration createNormalizedDuration( final JCas jCas, final String normalized ) {
      final Duration duration = new Duration( jCas );
      return normalizeDuration( duration, normalized );
   }

   /**
    * Durations from TimeNorm are represented with as a Period of time, e.g. "PT1H".
    * @param duration -
    * @param normalized TimeNorm text representation of normalized duration.
    * @return a ctakes representation of the duration.
    */
   static private Duration normalizeDuration( final Duration duration, final String normalized ) {
      duration.setTextRepresentation( normalized );
      final String text = normalized.trim();
      final boolean isTime = text.charAt( 1 ) == 'T';
      final int numberBegin = isTime ? 2 : 1;
      final int numberEnd = text.length()-1;
      final String unit = switch ( text.charAt( numberEnd ) ) {
         case 'S' -> "Second";
         case 'M' -> (isTime ? "Minute" : "Month");
         case 'H' -> "Hour";
         case 'D' -> "Day";
         case 'W' -> "Week";
         case 'Y' -> "Year";
         default -> "";
      };
      final String number = text.substring( numberBegin, numberEnd );
      duration.setNumber( number );
      duration.setNumberValue( getIntValue( number ) );
      duration.setUnit( unit );
      return duration;
   }

   /**
    * Durations from TimeNorm are represented with as a Period of time, e.g. "PT1H".
    * @param jCas -
    * @param begin begin character offset.
    * @param end end character offset.
    * @param normalized TimeNorm text representation of normalized duration.
    * @return a ctakes modifier representation of the duration with character offsets.
    */
   static private DurationModifier createDurationModifier( final JCas jCas, final int begin, final int end,
                                                           final String normalized ) {
      final Duration duration = createNormalizedDuration( jCas, normalized );
      final DurationModifier durationModifier = new DurationModifier( jCas, begin, end );
      durationModifier.setNormalizedForm( duration );
      durationModifier.addToIndexes( jCas );
      return durationModifier;
   }

   /**
    * Frequencies from TimeNorm are represented with as a Period of time, e.g. "PT1H".
    * @param jCas -
    * @param normalized TimeNorm text representation of normalized frequency.
    * @return a ctakes representation of the frequency.
    */
   static private Frequency createNormalizedFrequency( final JCas jCas, final String normalized ) {
      final String text = normalized.trim();
      final boolean isTime = text.charAt( 1 ) == 'T';
      final int numberBegin = isTime ? 2 : 1;
      final int numberEnd = text.length()-1;
      final String unit = switch ( text.charAt( numberEnd ) ) {
         case 'S' -> "Second";
         case 'M' -> (isTime ? "Minute" : "Month");
         case 'H' -> "Hour";
         case 'D' -> "Day";
         case 'W' -> "Week";
         case 'Y' -> "Year";
         default -> "";
      };
      final Frequency frequency = new Frequency( jCas );
      frequency.setTextRepresentation( text );
      final String number = text.substring( numberBegin, numberEnd );
      frequency.setNumber( number );
      frequency.setNumberValue( getIntValue( number ) );
      frequency.setUnit( unit );
      return frequency;
   }

   /**
    * Frequencies from TimeNorm are represented with as a Period of time, e.g. "PT1H".
    * @param jCas -
    * @param begin begin character offset.
    * @param end end character offset.
    * @param normalized TimeNorm text representation of normalized frequency.
    * @return a ctakes modifier representation of the frequency with character offsets.
    */
   static private FrequencyModifier createFrequencyModifier( final JCas jCas, final int begin, final int end,
                                                             final String normalized ) {
      final Frequency frequency = createNormalizedFrequency( jCas, normalized );
      final FrequencyModifier frequencyModifier = new FrequencyModifier( jCas, begin, end );
      frequencyModifier.setNormalizedForm( frequency );
      frequencyModifier.addToIndexes( jCas );
      return frequencyModifier;
   }

   /**
    *
    * @param text hopefully an integer represented by text
    * @return the parsed integer or Integer.MIN_VALUE if one could not be parsed.
    */
   static private int getIntValue( final String text ) {
      if ( text.isEmpty() ) {
         return Integer.MIN_VALUE;
      }
      try {
         return Integer.parseInt( text.trim() );
      } catch ( NumberFormatException nfE ) {
         LOGGER.warn( "Could not normalize number {}", text.trim() );
         return Integer.MIN_VALUE;
      }
   }

   /**
    * @param date a ctakes date, which contains text representation for year, month, and day.
    * @return a TimeNorm representation of the year, month, and day, or if that cannot be parsed then today's date.
    */
   static private TimeSpan getTimeSpan( final Date date ) {
      final int year = date.getYearValue();
      final int month = date.getMonthValue();
      final int day = date.getDayValue();
      if ( year + month + day > 0 ) {
         // if year || month || day is Integer.MIN then the total will be < 0
         return TimeSpan.of( year, month, day );
      }
      final LocalDate localDate = LocalDate.now();
      return TimeSpan.of( localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth() );
   }


}
