package org.healthnlp.lg.timelines;

import org.apache.ctakes.typesystem.type.refsem.Duration;
import org.apache.ctakes.typesystem.type.relation.TemporalRelation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SPF , chip-nlp
 * @since {4/20/2026}
 */
final public class TimeNormalUtil {

   static public final String INSTANT = "INSTANT";
   static public final String DURATION = "DURATION";
   static public final String FREQUENCY = "FREQUENCY";
   static public final String PRESENT_REF = "PRESENT_REF";

   private TimeNormalUtil() {}

   static final Function<TemporalRelation,String> getMed = t -> t.getArg1().getTextRepresentation();

   static public final String FAILED_NORMALIZATION = "FAILED_NORMALIZATION";
   // There should always be a time class, but just in case ...
   static final Function<TimeMention,String> getTimeClass
         = t -> t.getTimeClass() != null ? t.getTimeClass() : FAILED_NORMALIZATION;

   /**
    *
    * @param tlink ctakes temporal relation.
    * @return record with time class normalization type (e.g. instant, duration), normalized value, timex text.
    */
   static List<TimeNormal> createTimeNormals( final TemporalRelation tlink ) {
      final Map<String,List<TimeMention>> timeClassMentionsMap
            = tlink.getArg2().getMentions().stream()
                   .filter( TimeMention.class::isInstance )
                   .map( t -> (TimeMention)t )
                   .collect( Collectors.groupingBy( getTimeClass ) );
      final List<TimeNormal> timeNormals = new ArrayList<>();
      for ( Map.Entry<String,List<TimeMention>> timeClassMentions : timeClassMentionsMap.entrySet() ) {
         timeNormals.addAll( createTimeNormals( timeClassMentions.getKey(), timeClassMentions.getValue() ) );
      }
      return timeNormals;
   }


   /**
    *
    * @param timeClass time class normalization type (e.g. instant, duration),
    * @param timeMentions -
    * @return record with normalization type (e.g. instant, duration), normalized value, timex text.
    */
   static List<TimeNormal> createTimeNormals( final String timeClass, final List<TimeMention> timeMentions ) {
      return switch ( timeClass ) {
         case FAILED_NORMALIZATION ->
               timeMentions.stream().map( t -> new TimeNormal( timeClass, "", "", t.getCoveredText() ) )
                           .distinct().sorted().toList();
         case INSTANT ->
               timeMentions.stream().map( t -> new TimeNormal( timeClass, getY_M_D_H_m( t ), t.getDate().getTextRepresentation(), t.getCoveredText() ) )
                           .distinct().sorted().toList();
         case DURATION ->
               timeMentions.stream().map( t -> new TimeNormal( timeClass, getDuration( t ), t.getDuration().getTextRepresentation(), t.getCoveredText() ) )
                           .distinct().sorted().toList();
         default -> timeMentions.stream().map( t -> new TimeNormal( timeClass, "-", "-", t.getCoveredText() ) )
                                .distinct().sorted().toList();
      };
   }

   /**
    *
    * @param timeMention -
    * @return YYYY-MM-DD or empty if none.
    */
   static String getY_M_D( final TimeMention timeMention ) {
      final org.apache.ctakes.typesystem.type.refsem.Date date = timeMention.getDate();
      if ( date == null ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( date.getYearValue() );
      if  ( date.getMonthValue() > 0 ) {
         sb.append( '-' ).append( get2intText( date.getMonthValue() ) );
         if ( date.getDayValue() > 0 ) {
            sb.append( '-' ).append( get2intText( date.getDayValue() ) );
         }
      }
      return sb.toString();
   }

   /**
    *
    * @param timeMention -
    * @return HH:MM or empty if none.
    */
   static String getH_m( final TimeMention timeMention ) {
      final org.apache.ctakes.typesystem.type.refsem.Time time = timeMention.getTime();
      if ( time == null ) {
         return "";
      }
      return get2intText( time.getHourValue() )
            + ":" + get2intText( time.getMinuteValue() );
   }

   /**
    *
    * @param timeMention -
    * @return YYYY-MM-DD HH:MM if date and time are available, otherwise the one that is or empty if none.
    */
   static String getY_M_D_H_m( final TimeMention timeMention ) {
      final String instant = getY_M_D( timeMention ) + " " + getH_m( timeMention );
      return instant.trim();
   }

   /**
    *
    * @param timeMention -
    * @return Duration as # UNIT
    */
   static String getDuration( final TimeMention timeMention ) {
      final Duration duration = timeMention.getDuration();
      if ( duration != null ) {
         return duration.getNumberValue() + " " + duration.getUnit();
      }
      return "";
   }

   /**
    *
    * @param value -
    * @return text for int or single blank space if null.
    */
   static String get2intText( final Integer value ) {
      if ( value == null ) {
         return " ";
      }
      return String.format( "%02d", value );
   }

   record TimeNormal( String timeType, String timeNormal, String iso, String timex ) {}


}
