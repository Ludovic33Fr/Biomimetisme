package radio;

/*
 * CNN bio-inspiré pour la détection de pneumonies à partir de radiographies thoraciques.
 * Cette implémentation utilise DL4J (Deeplearning4j) et nécessite les dépendances :
 *   - org.deeplearning4j:deeplearning4j-core
 *   - org.nd4j:nd4j-native-platform
 *   - org.datavec:datavec-api
 *
 * L'architecture s'inspire de l'organisation hiérarchique du cortex visuel (V1, V2, V4, IT) :
 * quatre couches de convolution empilées extraient progressivement des caractéristiques
 * de complexité croissante, suivies de dropout + couche dense + softmax pour classification binaire.
 *
 * Voir le README du projet pour les instructions de build avec Maven/Gradle.
 */

import org.datavec.api.io.labels.ParentPathLabelGenerator;
import org.datavec.api.split.FileSplit;
import org.datavec.image.loader.NativeImageLoader;
import org.datavec.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.DropoutLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Random;

public class PneumoniaDetectionCNN {
    private static final Logger log = LoggerFactory.getLogger(PneumoniaDetectionCNN.class);

    private static final int HEIGHT = 224;
    private static final int WIDTH = 224;
    private static final int CHANNELS = 1;
    private static final int BATCH_SIZE = 32;
    private static final int NUM_CLASSES = 2;
    private static final int NUM_EPOCHS = 15;
    private static final int RANDOM_SEED = 42;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            log.error("Usage: PneumoniaDetectionCNN <cheminDonnees> <cheminModele>");
            System.exit(1);
        }

        String dataDir = args[0];
        String modelPath = args[1];

        Random random = new Random(RANDOM_SEED);

        File trainDir = new File(dataDir + "/train");
        FileSplit trainSplit = new FileSplit(trainDir, NativeImageLoader.ALLOWED_FORMATS, random);

        ParentPathLabelGenerator labelMaker = new ParentPathLabelGenerator();
        ImageRecordReader trainRecordReader = new ImageRecordReader(HEIGHT, WIDTH, CHANNELS, labelMaker);
        trainRecordReader.initialize(trainSplit);

        DataSetIterator trainIterator = new RecordReaderDataSetIterator(trainRecordReader, BATCH_SIZE, 1, NUM_CLASSES);

        DataNormalization scaler = new ImagePreProcessingScaler(0, 1);
        scaler.fit(trainIterator);
        trainIterator.setPreProcessor(scaler);

        File valDir = new File(dataDir + "/val");
        FileSplit valSplit = new FileSplit(valDir, NativeImageLoader.ALLOWED_FORMATS, random);
        ImageRecordReader valRecordReader = new ImageRecordReader(HEIGHT, WIDTH, CHANNELS, labelMaker);
        valRecordReader.initialize(valSplit);
        DataSetIterator valIterator = new RecordReaderDataSetIterator(valRecordReader, BATCH_SIZE, 1, NUM_CLASSES);
        valIterator.setPreProcessor(scaler);

        log.info("Construction du réseau...");

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(RANDOM_SEED)
            .weightInit(WeightInit.XAVIER)
            .updater(new Adam(0.001))
            .l2(0.0005)
            .list()
            // V1 : détection de caractéristiques de bas niveau (bords, orientations)
            .layer(0, new ConvolutionLayer.Builder(3, 3)
                .nIn(CHANNELS).nOut(32).stride(1, 1).padding(1, 1)
                .activation(Activation.RELU).build())
            .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                .kernelSize(2, 2).stride(2, 2).build())
            // V2 : textures et motifs
            .layer(2, new ConvolutionLayer.Builder(3, 3)
                .nOut(64).stride(1, 1).padding(1, 1).activation(Activation.RELU).build())
            .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                .kernelSize(2, 2).stride(2, 2).build())
            // V4 : formes plus complexes
            .layer(4, new ConvolutionLayer.Builder(3, 3)
                .nOut(128).stride(1, 1).padding(1, 1).activation(Activation.RELU).build())
            .layer(5, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                .kernelSize(2, 2).stride(2, 2).build())
            // IT : structures anatomiques
            .layer(6, new ConvolutionLayer.Builder(3, 3)
                .nOut(256).stride(1, 1).padding(1, 1).activation(Activation.RELU).build())
            .layer(7, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                .kernelSize(2, 2).stride(2, 2).build())
            // Dropout : inspiré de la redondance des réseaux biologiques
            .layer(8, new DropoutLayer.Builder(0.5).build())
            // Intégration des caractéristiques
            .layer(9, new DenseLayer.Builder()
                .nOut(512).activation(Activation.RELU).build())
            // Classification binaire
            .layer(10, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .nOut(NUM_CLASSES).activation(Activation.SOFTMAX).build())
            .setInputType(InputType.convolutional(HEIGHT, WIDTH, CHANNELS))
            .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        model.setListeners(new ScoreIterationListener(10));

        log.info("Entraînement...");

        for (int i = 0; i < NUM_EPOCHS; i++) {
            log.info("Époque " + (i + 1) + "/" + NUM_EPOCHS);
            model.fit(trainIterator);

            Evaluation eval = model.evaluate(valIterator);
            log.info(eval.stats());

            trainIterator.reset();
            valIterator.reset();
        }

        log.info("Sauvegarde : " + modelPath);
        ModelSerializer.writeModel(model, new File(modelPath), true);
    }

    public static double[] predict(String modelPath, String imagePath) throws Exception {
        MultiLayerNetwork model = ModelSerializer.restoreMultiLayerNetwork(new File(modelPath));

        NativeImageLoader loader = new NativeImageLoader(HEIGHT, WIDTH, CHANNELS);
        org.nd4j.linalg.api.ndarray.INDArray image = loader.asMatrix(new File(imagePath));

        DataNormalization scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(image);

        org.nd4j.linalg.api.ndarray.INDArray output = model.output(image);
        return output.toDoubleVector();
    }
}
