package ie.gmit.sw.neuralnetwork;

import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.encog.engine.network.activation.ActivationReLU;
import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.engine.network.activation.ActivationSoftMax;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.buffer.MemoryDataLoader;
import org.encog.ml.data.buffer.codec.CSVDataCODEC;
import org.encog.ml.data.buffer.codec.DataSetCODEC;
import org.encog.ml.data.folded.FoldedDataSet;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.RequiredImprovementStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.cross.CrossValidationKFold;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.util.csv.CSVFormat;
import org.encog.util.simple.EncogUtility;

import ie.gmit.sw.helpers.Utilities;
import ie.gmit.sw.interfaces.NeuralNetworkInterface;
import ie.gmit.sw.language.Language;

/**
 * @author Kevin Niland
 * @category Neural Network
 * @version 1.0
 *
 *          NeuralNetwork - Trains a neural network using different methods.
 *          From this, the accuracy of the neural network is determined. The
 *          trained neural network can then be used to predict the language of a
 *          file containing a string of text
 */
public class NeuralNetwork implements NeuralNetworkInterface {
	private Language[] languages;
	private BasicNetwork basicNetwork;
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
	private static int inputs = 300;
	private static final int outputs = 235;
	private int i, k = 5, actual = 0, correctValues = 0, epoch = 0, epochs, ideal, inputSize, result = -1,
			totalValues = 0;
//	private int hiddenLayers = (int) Math.sqrt(inputs + outputs);
	private int hiddenLayers = inputs / 3;
	private double errorRate, percent, limit = -1;

	public NeuralNetwork() {

	}

	/**
	 * 
	 * @param inputSize - Number of inputs (which also serves as the vector size)
	 * @param epochs    - Number of epochs to train the neural network for
	 * @param errorRate - Error rate to train the neural network to
	 */
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
	@Override
	public BasicNetwork configureTopology() {
		basicNetwork = new BasicNetwork();

		/**
		 * The dropout rate seems to be a major contributing factor to the accuracy of the neural network
		 */
		basicNetwork.addLayer(new BasicLayer(new ActivationReLU(), false, inputSize));
		basicNetwork.addLayer(new BasicLayer(new ActivationTANH(), true, hiddenLayers, 600));
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
	@Override
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
	@Override
	public void crossValidation(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		foldedDataSet = new FoldedDataSet(mlDataSet);
		mlTrain = new ResilientPropagation(basicNetwork, foldedDataSet);
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
		System.out.println("Epochs: " + epoch);
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
	@Override
	public void resilientPropagation(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		resilientPropagation = new ResilientPropagation(basicNetwork, mlDataSet);
		resilientPropagation.addStrategy(new RequiredImprovementStrategy(5));

		/**
		 * Trains the neural network until the error rate gets below errorRate, as
		 * defined by the user
		 */
		EncogUtility.trainToError(resilientPropagation, errorRate);

		// Save the neural network and finish training
		Utilities.saveNeuralNetwork(basicNetwork, "./resilient.nn");
		resilientPropagation.finishTraining();
	}

	/**
	 * Determine the accuracy of the neural network.
	 * 
	 * @param basicNetwork
	 * @param mlDataSet
	 */
	@Override
	public void getAccuracy(BasicNetwork basicNetwork, MLDataSet mlDataSet) {
		for (MLDataPair mlDataPair : mlDataSet) {
			mlDataActual = basicNetwork.compute(mlDataPair.getInput());
			mlDataIdeal = mlDataPair.getIdeal();

			for (i = 0; i < mlDataActual.size(); i++) {
				if (mlDataActual.getData(i) > 0) {
					if (result == -1 || (mlDataActual.getData(i) > mlDataActual.getData(result))) {
						result = i;
					}
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

		/**
		 * This might be the more correct way of calculating the accuracy (?). This
		 * method yields an accuracy of about 10% less than the above method so feel
		 * free to mess around with this
		 */
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
//					ideal = i;
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

	/**
	 * Predicts the language of a file using one of the trained neural networks
	 * 
	 * @param nnFile - Neural network file user has chosen to predict the language
	 *               of a file
	 * @param vector - Vector array containing normalized values of file containing
	 *               the string of text
	 */
	@Override
	public void getPrediction(String nnFile, double[] vector) {
		mlDataPredction = new BasicMLData(vector);
		mlDataPredction.setData(vector);

		basicNetwork = Utilities.loadNeuralNetwork(nnFile);
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