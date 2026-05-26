from ctakes_pbj.pipeline.pbj_pipeline import PBJPipeline
from ctakes_pbj.component.pbj_receiver import PBJReceiver
from ctakes_pbj.component.pbj_sender import PBJSender

from lg_timelines.lgt_graph_ae import LGTGraphAE


def main():
    # Create a PBJ Pipeline.
    pipeline = PBJPipeline()
    # Add the PBJReceiver component.  This connects to the Artemis broker and retrieves information.
    pipeline.reader(PBJReceiver())
    # Add the PBJReceiver component.  This connects to the Artemis broker and retrieves information.
    pipeline.add(PBJSender())
    # Add the LGT Graph component.
    pipeline.add(LGTGraphAE())
    # Start running the pipeline.
    pipeline.run()


main()



