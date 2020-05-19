package ie.gmit.sw.neuralnetwork;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.encog.Encog;
import org.encog.Test;
import org.encog.engine.network.activation.ActivationReLU;
import org.encog.engine.network.activation.ActivationSoftMax;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.ml.data.buffer.MemoryDataLoader;
import org.encog.ml.data.buffer.codec.CSVDataCODEC;
import org.encog.ml.data.buffer.codec.DataSetCODEC;
import org.encog.ml.data.folded.FoldedDataSet;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.RequiredImprovementStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.cross.CrossValidationKFold;
import org.encog.neural.networks.training.propagation.back.Backpropagation;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.util.csv.CSVFormat;
import org.encog.util.simple.EncogUtility;

import ie.gmit.sw.helpers.Utilities;
import ie.gmit.sw.language.Language;

/**
 * @author Kevin Niland
 * @category Neural Network
 * @version 1.0
 *
 *          NeuralNetwork
 */
public class NeuralNetwork {
	private Language[] languages;
	private BasicNetwork basicNetwork, savedNetwork;
	private CrossValidationKFold crossValidationKFold;
	private DataSetCODEC dataSetCODEC;
	private FoldedDataSet foldedDataSet;
	private MemoryDataLoader memoryDataLoader;
	private MLData mlDataActual, mlDataIdeal, mlDataOutput, mlDataPredction;
	private MLDataSet mlDataSet;
	private MLTrain mlTrain;
	private ResilientPropagation resilientPropagation;
	private DecimalFormat decimalFormat;
	private File csvFile = new File("data.csv");
	private static int inputs = 510;
	private static final int outputs = 235;
	private int i, k = 5, actual = 0, correctValues = 0, epoch = 0, epochs, ideal, inputSize, result = -1,
			totalValues = 0;
	private int hiddenLayers = inputs / 4;
	private static final double MAX_ERROR = 0.0023;
	private double error, percent, limit = -1, errorRate;

	public NeuralNetwork() {

	}

	public NeuralNetwork(int inputSize, int epochs, double errorRate) {
		this.inputSize = inputSize;
		this.epochs = epochs;
		this.errorRate = errorRate;
	}

	/**
	 * Configures the network topology
	 * 
	 * @return basicNetwork
	 */
	public BasicNetwork configureTopology() {
		basicNetwork = new BasicNetwork();

		basicNetwork.addLayer(new BasicLayer(new ActivationReLU(), true, inputSize));
		basicNetwork.addLayer(new BasicLayer(new ActivationReLU(), true, hiddenLayers, 400));
		basicNetwork.addLayer(new BasicLayer(new ActivationSoftMax(), false, outputs));

		basicNetwork.getStructure().finalizeStructure();
		basicNetwork.reset();

		return basicNetwork;
	}

	/**
	 * Generate the dataset from the CSV file generated by VectorProcessor.java
	 * 
	 * @return mlDataSet
	 */
	public MLDataSet generateDataSet() {
		dataSetCODEC = new CSVDataCODEC(csvFile, CSVFormat.DECIMAL_POINT, false, inputSize, outputs, false);
		memoryDataLoader = new MemoryDataLoader(dataSetCODEC);
		mlDataSet = memoryDataLoader.external2Memory();

		return mlDataSet;
	}

	/**
	 * Trains a neural network using K-Fold Cross Validation, where K is 5
	 * 
	 * @param basicNetwork
	 * @param mlDataSet
	 */
	public void crossValidation(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		foldedDataSet = new FoldedDataSet(mlDataSet);
		mlTrain = new Backpropagation(basicNetwork, foldedDataSet);
		crossValidationKFold = new CrossValidationKFold(mlTrain, k);

		// Format crossValidationKFold output
		decimalFormat = new DecimalFormat("#.######");
		decimalFormat.setRoundingMode(RoundingMode.CEILING);

		/**
		 * Train the neural network for n number of epochs, as defined by the user. From
		 * testing, the ideal number of epochs i.e. the ideal number of epochs to train
		 * the neural network for until the error rate starts to plateau, seems to be
		 * ...
		 */
		do {
			crossValidationKFold.iteration();

			epoch++;

			System.out.println("Epoch: " + epoch);
			System.out.println("Error: " + decimalFormat.format(crossValidationKFold.getError()));
		} while (epoch < epochs);

		// Output the results
		System.out.println("\nINFO: Training complete");
		System.out.println(epoch + " epochs");
		System.out.println("Error rate: " + decimalFormat.format(crossValidationKFold.getError()));

		// Save the neural network to a .nn file and finish training
		Utilities.saveNeuralNetwork(basicNetwork, "./kfold.nn");
		crossValidationKFold.finishTraining();
	}

	/**
	 * Trains a neural network using resilient propagation
	 * 
	 * @param basicNetwork
	 * @param mlDataSet
	 */
	public void resilientPropagation(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		resilientPropagation = new ResilientPropagation(basicNetwork, mlDataSet);
		resilientPropagation.addStrategy(new RequiredImprovementStrategy(5));

		EncogUtility.trainToError(resilientPropagation, errorRate);

		Utilities.saveNeuralNetwork(basicNetwork, "./resilient.nn");
		resilientPropagation.finishTraining();
	}

	/**
	 * Determine the accuracy of the neural network.
	 * 
	 * @param basicNetwork
	 * @param mlDataSet
	 */
	public void getAccuracy(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		for (MLDataPair mlDataPair : mlDataSet) {
			mlDataActual = basicNetwork.compute(mlDataPair.getInput());
			mlDataIdeal = mlDataPair.getIdeal();

			for (i = 0; i < mlDataActual.size(); i++) {
				if (mlDataActual.getData(i) > 0
						&& (result == -1 || (mlDataActual.getData(i) > mlDataActual.getData(result)))) {
					result = i;
				}
			}

			for (i = 0; i < mlDataIdeal.size(); i++) {
				if (mlDataIdeal.getData(i) == 1) {
					ideal = i;

					if (result == ideal) {
						correctValues++;
					}
				}
			}

			totalValues++;
		}

//		for (MLDataPair mlDataPair : mlDataSet) {
//			mlDataActual = basicNetwork.compute(mlDataPair.getInput());
//
//			for (i = 0; i < mlDataActual.size(); i++) {
//				if (mlDataActual.getData(i) > limit) {
//					limit = mlDataActual.getData(i);
//
//					actual = i;
//				}
//			}
//
//			for (i = 0; i < mlDataActual.size(); i++) {
//				if (mlDataPair.getIdeal().getData(i) > limit) {
//					limit = mlDataActual.getData(i);
//
//					ideal = i;\
//
//					if (actual == ideal) {
//						correctValues++;
//					}
//				}
//			}
//
//			totalValues++;
//		}

		// Format accuracy
		decimalFormat = new DecimalFormat("##.##");
		decimalFormat.setRoundingMode(RoundingMode.CEILING);

		percent = (double) correctValues / (double) totalValues;

		System.out.println("\nINFO: Testing complete.");
		System.out.println("Correct: " + correctValues + "/" + totalValues);
		System.out.println("Accuracy: " + decimalFormat.format(percent * 100) + "%");
	}

	public void getPrediction(double[] vector) {
		mlDataPredction = new BasicMLData(vector);
		mlDataPredction.setData(vector);

		basicNetwork = Utilities.loadNeuralNetwork("./kfold.nn");
		mlDataOutput = basicNetwork.compute(mlDataPredction);

		for (i = 0; i < mlDataOutput.size(); i++) {
			if (mlDataOutput.getData(i) > limit) {
				limit = mlDataOutput.getData(i);
				actual = i;
			}
		}

		languages = Language.values();

		System.out.println("Predicted language: " + languages[actual].toString());
	}
}