# Systemic Anticancer Therapy (SACT) Timeline Extractor.  
**LangGraph Timelines (LGT)**, SACT release (LGT SACT).

A [LangGraph](https://www.langchain.com/langgraph) and [Apache cTAKES-PBJ](https://github.com/apache/ctakes) -based pipeline for extracting and normalizing temporal information from clinical/patient notes using fine-tuned LLMs.  
Specifically, Systemic Anticancer Therapy (SACT) Timelines.

## Overview

This project implements a multi-stage workflow that extracts temporal triplets (events, times, relations) from text using a fine-tuned [Qwen](https://huggingface.co/Qwen) model.

## Prerequisites

- A system with a [CUDA-enabled GPU](https://developer.nvidia.com/cuda/gpus)
- Python 3.10 or later
- Java 17.x
- [Apache Maven](https://maven.apache.org/) 3.x
- [Apache Artemis](https://artemis.apache.org/components/artemis/) 
- [HuggingFace Account](https://huggingface.co/welcome)
- [Access token](https://huggingface.co/docs/hub/en/security-tokens) for the [HealthNLP](https://huggingface.co/HealthNLP) [tuned model](https://huggingface.co/HealthNLP/finetuned_qwen3_14b_lora_chemotimelines) 

A GPU with more than 32GB RAM is required to obtain the best results, running with full 16-bit floating point accuracy.
GPUs with less memory can still be used.  A GPU with more than 18GB RAM is required to run with 8-bit integer accuracy.
A smaller GPU will run with 4-bit quantization and reduced accuracy.

### Installing Apache Maven
1. [Install Apache Maven](https://maven.apache.org/install.html)

### Installing Apache Artemis
1. [Install Apaceh Artemis](https://artemis.apache.org/components/artemis/documentation/latest/using-server.html#installation)
2. [Create a Broker](https://artemis.apache.org/components/artemis/documentation/latest/using-server.html#creating-a-broker-instance)


## Project Structure

### Source Code Structure
```
lgt source root/
├── build_CPU_LGT.sh                   # Script to build LGT SACT for systems without a GPU. 
├── build_GPU_LGT.sh                   # Script to build LGT SACT for systems with a GPU (recommended). 
├── build_only_LGT.sh                  # Script to rebuild LGT SACT if the source code has been modified. 
├── LICENSE                            # Legal License for use of SACT Timelines.
├── pom.xml                            # Definition of project for Apache Maven.
└── README.md                          # This file, general project information.
   lg-timelines-bin/                   # Sub-Project to build the final binary installation. 
      target/                          # Does not exist until you run a build script.
         lg-timelines-1.0.0-bin/       # The Runnable Binary.  Created by the build script.
   lg-timelines-j/                     # Sub-Project with Java code used for Normalizer and Output. 
   lg-timelines-py/                    # Sub-Project with Python code used for TLink extraction. 
   pbj-langgraph-py/                   # Sub-Project extending cTAKES-PBJ for use with LangGraph. 
   pbj-llm-tools-py/                   # Sub-Project extending cTAKES-PBJ for use with LLMs. 
   pip-torch-cuda/                     # Sub-Project used to add 3rd-party components for LLMs.
```

### Runnable Binary Structure
```
lg-timelines-1.0.0-bin/
├── README.md                          # This file, general project information.
└── LICENSE                            # Legal License for use of SACT Timelines.
   bin/                                # Sub-Project to build the final binary installation. 
   └── runLGT.sh                       # Script to run LGT SACT on Linux (recommended).
   └── runLGT_CPU.sh                   # Script to run LGT SACT on Linux without a GPU.
   config/                             # Library files and configution required for logging.
   lib/                                # Library files required to run LGT SACT.
   python/                             # Intermediate python modules created to run LGT SACT.
   resources/                          # Resource files required to run LGT SACT.
      pipeline/                        # Pipeline definition files required to run LGT SACT.
      └── LangGraphTimelines.piper     # Entry pipeline definition script. 
      prompt/                          # Directory containing LLM Prompt required to run LGT SACT.
      └── buildSACTTimeline.txt        # LLM Prompt required to run LGT SACT. 
      sample_notes/                    # Sample notes that can be used to test LGT SACT.
          text/                        # Corpus directory containing patient subdirectories with notes.
      sample_output/                   # Output from Sample notes.
```

## Building

After [installing Apache Maven](#Installing-Apache-Maven) and [installing Apache Artemis](#Installing-Apache-Artemis), 
you must build the project [source code](#Source-Code-Structure) into a [runnable binary](#Runnable-Binary-Structure).  
The best way to do this is with one of the build scripts provided with the source files.  
If you have a system with a GPU, use the `build_GPU_LGT.sh` script. Otherwise, use the `build_CPU_LGT.sh` script. 
It is **highly recommended** that you use a system with a GPU. CPU-only systems may be very slow in processing or may not work at all. 
If you are using CUDA version 12.6 or 12.8, when you run the `build_GPU_LGT.sh` script, add the command line parameter `-Dcuda.version=<version>`, 
where `<version>` is *126* or *128*.  For instance:
```bash
./build_GPU_LGT.sh -Dcuda.version=128
```
You do not need to use this parameter for CUDA 13.0. 
To find your CUDA version or compatibility, you can run one of the following commands:
```bash
nvcc -V
```
```bash
nvidia-smi
```

After running the build script there will be a new directory in `lgt source root/lg-timelines-bin/target/` named `lg-timelines-1.0.0-bin/`. This is the [runnable binary](#Runnable-Binary-Structure).  
You can run the project within this directory, but it is recommended that you move the `lg-timelines-1.0.0-bin/` directory to another location. 
The behavior of the runnable binary can be customized by modifying the [piper files](https://github.com/apache/ctakes/wiki/Piper-Files) in the `resources/pipeline/` directory, 
and moving the binary to another location can help prevent accidental loss of customizations upon rebuilding. 

The first time you run LGT SACT, it will fetch and install the [Qwen3-14b](https://huggingface.co/Qwen/Qwen3-14B) LLM from [HuggingFace](https://huggingface.co/).  
It will also download the SACT [fine-tuned model](https://huggingface.co/HealthNLP/finetuned_qwen3_14b_lora_chemotimelines).  
Before you run the first time, you may need to register and obtain a [HuggingFace token](https://huggingface.co/docs/hub/en/security-tokens) for its use.  
You can visit the page on the [fine-tuned model](https://huggingface.co/HealthNLP/finetuned_qwen3_14b_lora_chemotimelines) for access.

## Running

Within the `lg-timelines-1.0.0-bin/` directory there is a `bin/` directory containing a script named `runLGT.sh`. 
To use the `runLGT.sh` script you must specify values for the following Command-Line Parameters:
```
InputDirectory (-i)      # The directory containing clinical notes.
OutputDirectory (-o)     # The directory to which output files should be written.
ArtemisBroker (-a)       # The directory to an Apache Artemis broker. 
(++hf_token)             # The token for your HuggingFace account connected to langgraph-timelines.
```
> [!NOTE]
> `path/to/myBroker` should point to the directory you created when [Installing Apache Artemis](#Installing-Apache-Artemis)

> [!NOTE]
> The parameter ++hf_token, if required (not set in environment), must be the last parameter on the command-line.

Run the complete pipeline:
```bash
./bin/runLGT.sh -i path/to/myDocs -o path/to/myOutput -a /path/to/my_broker ++hf_token abc123
```

This script runs a multi-stage workflow that:

1. Starts an [Apache Artemis](https://artemis.apache.org/components/artemis/) Broker.
2. Runs an [Apache cTAKES](https://github.com/apache/ctakes) document file reader.
3. Extracts temporal triplets (events, times, relations) from text using a fine-tuned [Qwen](https://huggingface.co/Qwen) model.
4. Normalizes temporal expressions using [HNLP-TimeNorm](https://github.com/HealthNLPorg/hnlp-timenorm).
5. Aggregates note-level data into patient-level timelines and writes output.
6. Shuts down the [Apache Artemis](https://artemis.apache.org/components/artemis/) broker.

Steps 2 through 5 are run in a loop over the entire document corpus using an [Apache Artemis](https://github.com/apache/artemis) Broker to handle communication between processes in queues.


### Input Data Format

The pipeline expects directories containing text files as input:
```
corpus/                                # Corpus directory.  Point to this directory when running.
   patient_A/                          # Patient directory.
   ├── Document_1.txt                  # Plaintext file containing clinical note.
   ├── Document_2.txt                  # Plaintext file containing clinical note.
   └── Document_3.txt                  # Plaintext file containing clinical note.
   patient_B/                          # Patient directory.
   ├── Document_1.txt                  # Plaintext file containing clinical note.
   ├── Document_2.txt                  # Plaintext file containing clinical note.
   └── Document_3.txt                  # Plaintext file containing clinical note.
```
The sample notes included with this project in `resources/sample_notes/text/` each contain a header similar to the following:
```text
===================================================================
Report ID.....................report01_PRG
Patient ID....................PT001234
Patient Name..................Sample Patient A
Principal Date................20231115 0930
Record Type...................ONCOLOGY PROGRESS NOTE
===================================================================
[Report de-identified (Limited dataset compliant) by De-ID v.6.24.5.1]
```
In its default configuration, LGT SACT parses these headers to obtain the document creation time from the line:
```text
Principal Date................20231115 0930
```
The document creation time is used during time normalization to properly normalize relative temporal expressions such as "today" or "last week". 
These document headers are parsed by Java code in the file `PittHeaderParser`, which can be found in the **lg-timelines-j** sub-project. 
The PittHeaderParser is added to the workflow in the file `InstitutePipe.piper`, which can be found in the main `resources/` directory. 
These can be removed or replaced to implement other means of setting the document creation time. 
If no special means are used, then the system will use the timestamp of the file containing each document as the document creation time.

### Output Data Format

The pipeline creates a directory containing a json file, log files, and patient/note tables as output:
```
output root/                           # Output directory.  Point to this directory when running.
├── ctakes_artemis_start.log           # Log file from Apache Artemis.
├── ctakes_artemis_stop.log            # Log file from Apache Artemis.  Normally empty.
├── ctakes_LGT_output.log              # Log file from the Output Writer.
├── ctakes_Normlize_Times.log          # Log file from the Time Normalizer.
├── lgt_main_py.log                    # Log file from the main TLink Extractor.
└── Corpus_medTlinks.json              # JSON file containing SACT Timelines for all patients in the corpus.
   patient_A/                          # Patient output directory.
   ├── Document_1_medTimes.html        # Table containing SACT and temporal information for Document_1.
   ├── Document_2_medTimes.html        # Table containing SACT and temporal information for Document_2.
   └── Document_3_medTimes.html        # Table containing SACT and temporal information for Document_3.
   patient_B/                          # Patient output directory.
   ├── Document_1_medTimes.html        # Table containing SACT and temporal information for Document_1.
   ├── Document_2_medTimes.html        # Table containing SACT and temporal information for Document_2.
   └── Document_3_medTimes.html        # Table containing SACT and temporal information for Document_3.
```

#### JSON Output File Format

```json
{
  "PT001234": [
    ["Anastrozole", "BEGINS-ON", "2023-10-15"],
    ["Cyclophosphamide", "ENDS-ON", "2023-09-18"]
  ],
  "PT005678": [
    ["Dabrafenib", "BEGINS-ON", "2023-09-15"],
    ["Ipilimumab", "BEGINS-ON", "2022-07-20"],
    ["Ipilimumab", "CONTAINS-1", "2022-10-05"]
  ]
}
```
In the json, there is an entry for each patient consisting of the patient ID and a list of the patient's SACT events, their relations to times (TLinks), and the normalized form of times. 

**SACT Event**  
The Systemic Anticancer Therapy (SACT) in output files is reported by the LLM, and may be exact text from the document or, rarely, some variant thereof.
The text may be the name of a medication, an acronym for a combination therapy, or descriptive categories such as "radiation" or "chemotherapy".

**TLink Type**  
The type of temporal relation between a SACT and Time.
- Begins-On : SACT begins on the specified Time
- Contains-1 : SACT is inverse contains the specified time.
- Ends-On : SACT ends on the specified Time.

It may be easier to think of `Contains-1` as "*occurs at*" or "*occurs during*" rather than "*inverse contains*".  
The entire TLink can be read: "*SACT TLink-Type Time*".
For instance: "*chemotherapy begins on 1975-3-25*" or "*paclitaxel occurs at 1975-3-25*".

**Normalized Time**  
Times may be expressed as an absolute *instant* of Time in `YYYY-MM-DD hh:mm` format.  For instance `1975-3-25 10:15`.  
Times may also be expressed as a *duration* in Amount Unit format.  For instance, `3 Weeks`.  
Times may also be a mixture of a duration that occurs at some instant.  For instance `1975 w3` is the third week of 1975.  
This does not mean that the intant was the first day of that week, nor does it mean that the duration was the entire week.
Another example of this instant/duration mix would be `1975`, which most likely refers to some unspecified time within the year 1975,
but it could also refer to the entire or most of the year.


#### HTML Table Output File Format

<!DOCTYPE html>
<html>
<head>
<style>
table {
  margin-left: auto;
  margin-right: auto;
}
</style>
</head>
<body>

<table>
 <thead>
  <tr>
    <th> Medication </th>
    <th> Medication Text </th>
    <th> Temporal Relation </th>
    <th> Time Type </th>
    <th> TimeNorm ISO </th>
    <th> Normalized Time </th>
    <th> Temporal Expression </th>
  </tr>
 </thead>
  <tr>
    <td>-- SACT --</td>
    <td>Dabrafenib</td>
    <td>BEGINS-ON</td>
    <td>INSTANT</td>
    <td>2023-09-15</td>
    <td>2023-09-15 00:00</td>
    <td>September 15, 2023</td>
  </tr>
  <tr>
    <td>-- SACT --</td>
    <td>Dabrafenib</td>
    <td>CONTAINS-1</td>
    <td>DURATION</td>
    <td>P6M</td>
    <td>6 Month</td>
    <td>6 months</td>
  </tr>
  <tr>
    <td>-- SACT --</td>
    <td>Ipilimumab</td>
    <td>ENDS-ON</td>
    <td>INSTANT</td>
    <td>2022-07-20</td>
    <td>2022-07-20 00:00</td>
    <td>July 20, 2022</td>
  </tr>
  <tr>
    <td>-- SACT --</td>
    <td>Ipilimumab</td>
    <td>CONTAINS-1</td>
    <td>INSTANT</td>
    <td>2022-10-05</td>
    <td>2022-10-05 00:00</td>
    <td>October 5, 2022</td>
  </tr>
 <tfoot>
  <tf>
  </tf>
 <tfoot>
</table>
</body>
</html>

**Medication**  
For this release, Medications are not normalized.  If a drug is written with two different names in a document, both ways may be represented in the output. 
Normalization would recognize that the two names refer to the same drug and represent both mentions with a single normalized form. 
Since there is no normalization, the placeholder `--SACT--` is written.  

**Medication Text**  
May be exact text from the document or some variant thereof.
The text may be the name of a medication, an acronym for a combination therapy, or descriptive categories such as "radiation" or "chemotherapy".

**Temporal Relation**  
The type of temporal relation between a SACT and Time.
- Begins-On : SACT begins on the specified Time
- Contains-1 : SACT *inverse contains* the specified time.
- Ends-On : SACT ends on the specified Time.

It may be easier to think of `Contains-1` as "*occurs at*" or "*occurs during*" rather than "*inverse contains*".  
The entire TLink can be read: "*SACT TLink-Type Time*".
For instance: "*chemotherapy begins on 1975-3-25*" or "*paclitaxel occurs at 1975-3-25*".

**Time Type**  
LGT SACT can extract two types of time:
- Instant : A singular moment in time that can be placed upon a timeline.  For instance, `1975-3-25`.
- Duration : A span of time that cannot be directly placed upon a timeline.  For instance, `3 Weeks`.

**TimeNorm ISO**  
Time Normalization is performed using the external project **[HNLP-TimeNorm](https://github.com/HealthNLPorg/hnlp-timenorm)**. 
HNLP-TimeNorm provides a normalized value for temporal expressions based uopn the [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html) standard.   

**Normalized Time**  
Times may be expressed as an absolute *instant* of Time in `YYYY-MM-DD hh:mm` format.  For instance `1975-3-25 10:15`.  
Times may also be expressed as a *duration* in Amount Unit format.  For instance, `3 Weeks`.  
Times may also be a mixture of a duration that occurs at some instant.  For instance `1975 w3` is the third week of 1975.  
This does not mean that the intant was the first day of that week, nor does it mean that the duration was the entire week.
Another example of this instant/duration mix would be `1975`, which most likely refers to some unspecified time within the year 1975,
but it could also refer to the entire or most of the year.

**Temporal Expression**  
The text reported by the LLM, which may be exact text from the document or, rarely, some variant thereof. 
This expression can be absolute or relative, for instance `next week`. 

##### Customizing the Table File Type

You can set the table file type to be one of the following:
- BSV : Bar-Separated Value
- CSV : Comma-Separated Value
- TAB : Tab-separated Value
- HTML:  HyperText Markup Language. 

*BSV* files are the easiest to import into some other tools, such as NLP applications. They can also be imported into spreadsheet applications.    
*CSV* files are the easiest to automatically import into spreadsheet applications.  
*TAB* files can be slightly more readable than others. They can also be imported into spreadsheet applications.  
*HTML* files are very readable in a web browser.  This is the default table file type for LGT SACT.

To change the format of the output table files, change the value of the parameter `TableType` in the `LGT_Output.piper` file in the `resources/pipeline/` directory.
```
// You can change the file type for output tables to one of the following: BSV, CSV, TAB, or HTML.
set TableType=HTML
add MedTimeTableFileWriter
```

### Expected Output

The `sample_output/` directory contains sample outputs for the sample notes.
Please note that LGT SACT has been designed to adapt its LLM for GPUs of different RAM sizes to run on smaller systems, so results may be different.
In addition, LLMs are by nature non-deterministic, meaning that results may not always be identical between repeated runs.

### Empty output files

If the output files are empty, check the file `lgt_main_py.log` for the following line:
```
LGT Graph will not run as there are no GPUs and --use-cpu is not set to yes.
```
If you see this line then the system could not find a CUDA-enabled GPU.
If you have a CUDA-enabled GPU then make certain that [NVIDIA drivers](https://www.nvidia.com/en-us/drivers/) for the gpu are installed.  
If you do not have a GPU, make sure you are running the script `runLGT_CPU.sh` and not `runLGT.sh`.


## Acknowledgments

This software was created as part of the **[Cancer Deep Phenotyping (DeepPhe)](https://deepphe.github.io/)** project, 
supported by the [National Cancer Institute's Information Technology for Cancer Research (ITCR) initiative](https://www.cancer.gov/about-nci/organization/cssi/research/itcr) 
(Grant #U24CA248010).

This system re-implements the winning approach from the **[ChemoTimeline shared task](https://aclanthology.org/2025.clinicalnlp-1.1/)**, originally developed by UW-BioNLP.  
Our implementation follows their content extraction approach with minor changes.  
The system was architected for ease of use and adaptation and followed current state of the art in Agent design.

- **Task Overview:** Yao et al. "Overview of the 2025 Shared Task on
Chemotherapy Treatment Timeline Extraction". *Proceedings of the 7th Clinical Natural Language Processing Workshop*, 2025. [[Paper]](https://aclanthology.org/2025.clinicalnlp-1.1/)
- **UW-BioNLP:** Zhang et al. "UW-BioNLP at ChemoTimelines 2025: Thinking, Fine-Tuning, and
Dictionary-Enhanced LLM Systems for Chemotherapy Timeline Extraction". *Proceedings of the 7th Clinical Natural Language Processing Workshop*, 2025. [[Paper]](https://aclanthology.org/2025.clinicalnlp-1.6/)

This module uses **[HNLP-TimeNorm](https://github.com/HealthNLPorg/hnlp-timenorm)** for time expression normalization.  
The implementation is a Java adaptation of the [original Scala version](https://github.com/clulab/timenorm) developed by the
Computational Language Understanding (CLU) Lab at the University of Arizona.

