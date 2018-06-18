/*===============================================================================
 * Copyright (c) 2010-2012 University of Massachusetts.  All Rights Reserved.
 *
 * Use of the RankLib package is subject to the terms of the software license set
 * forth in the LICENSE file included with this software, and also available at
 * http://people.cs.umass.edu/~vdang/ranklib_license.html
 *===============================================================================
 */

package ciir.umass.edu.learning.neuralnet;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vdang
 *
 * This class implements individual neurons in the network.
 */
public class Neuron {
    public static double momentum = 0.9;
    public static double learningRate = 0.001;//0.001;

    //protected TransferFunction tfunc = new HyperTangentFunction();
    protected TransferFunction tfunc = new LogiFunction();

    protected double output;//sigmoid(wsum) (range from 0.0 to 1.0): output for the current input
    protected List<Double> outputs = null;
    protected double delta_i = 0.0;
    protected double[] deltas_j = null;

    protected List<Synapse> inLinks = null;
    protected List<Synapse> outLinks = null;

    public Neuron() {
        output = 0.0;
        inLinks = new ArrayList<>();
        outLinks = new ArrayList<>();

        outputs = new ArrayList<>();
        delta_i = 0.0;
    }

    public double getOutput() {
        return output;
    }

    public double getOutput(final int k) {
        return outputs.get(k);
    }

    public List<Synapse> getInLinks() {
        return inLinks;
    }

    public List<Synapse> getOutLinks() {
        return outLinks;
    }

    public void setOutput(final double output) {
        this.output = output;
    }

    public void addOutput(final double output) {
        outputs.add(output);
    }

    public void computeOutput() {
        Synapse s = null;
        double wsum = 0.0;
        for (int j = 0; j < inLinks.size(); j++) {
            s = inLinks.get(j);
            wsum += s.getSource().getOutput() * s.getWeight();
        }
        output = tfunc.compute(wsum);//using the specified transfer function to compute the output
    }

    public void computeOutput(final int i) {
        Synapse s = null;
        double wsum = 0.0;
        for (int j = 0; j < inLinks.size(); j++) {
            s = inLinks.get(j);
            wsum += s.getSource().getOutput(i) * s.getWeight();
        }
        output = tfunc.compute(wsum);//using the specified transfer function to compute the output
        outputs.add(output);
    }

    public void clearOutputs() {
        outputs.clear();
    }

    /**
     * Compute delta for neurons in the output layer. ONLY for neurons in the output layer.
     * @param targetValue
     */
    public void computeDelta(final PropParameter param) {
        /*double pij = (double) (1.0 / (1.0 + Math.exp(-(prev_output-output))));
        prev_delta = (targetValue-pij) * tfunc.computeDerivative(prev_output);
        delta =      (targetValue-pij) * tfunc.computeDerivative(output);*/
        final int[][] pairMap = param.pairMap;
        final int current = param.current;

        delta_i = 0.0;
        deltas_j = new double[pairMap[current].length];
        for (int k = 0; k < pairMap[current].length; k++) {
            final int j = pairMap[current][k];
            float weight = 1;
            double pij = 0;
            if (param.pairWeight == null)//RankNet, no pair-weight needed
            {
                weight = 1;
                pij = 1.0 / (1.0 + Math.exp(outputs.get(current) - outputs.get(j)));//this is in fact not "pij", but "targetValue-pij":  1 - 1/(1+e^{-o_ij})
            } else//LambdaRank
            {
                weight = param.pairWeight[current][k];
                pij = param.targetValue[current][k] - 1.0 / (1.0 + Math.exp(-(outputs.get(current) - outputs.get(j))));
            }
            final double lambda = weight * pij;
            delta_i += lambda;
            deltas_j[k] = lambda * tfunc.computeDerivative(outputs.get(j));
        }
        delta_i *= tfunc.computeDerivative(outputs.get(current));
        //(delta_i * input_i) - (sum_{delta_j} * input_j) is the *negative* of the gradient, which is the amount of weight should be added to the current weight
        //associated to the input_i
    }

    /**
     * Update delta from neurons in the next layer (back-propagate)
     */
    public void updateDelta(final PropParameter param) {
        /*double errorSum = 0.0;
        Synapse s = null;
        for(int i=0;i<outLinks.size();i++)
        {
        	s = outLinks.get(i);
        	errorSum += (s.getTarget().getPrevDelta()-s.getTarget().getDelta()) * s.getWeight();
        }
        prev_delta = errorSum * tfunc.computeDerivative(prev_output);
        delta =      errorSum * tfunc.computeDerivative(output);*/
        final int[][] pairMap = param.pairMap;
        final float[][] pairWeight = param.pairWeight;
        final int current = param.current;
        delta_i = 0;
        deltas_j = new double[pairMap[current].length];
        for (int k = 0; k < pairMap[current].length; k++) {
            final int j = pairMap[current][k];
            final float weight = (pairWeight != null) ? pairWeight[current][k] : 1.0F;
            double errorSum = 0.0;
            for (int l = 0; l < outLinks.size(); l++) {
                final Synapse s = outLinks.get(l);
                errorSum += s.getTarget().deltas_j[k] * s.weight;
                if (k == 0) {
                    delta_i += s.getTarget().delta_i * s.weight;
                }
            }
            if (k == 0) {
                delta_i *= weight * tfunc.computeDerivative(outputs.get(current));
            }
            deltas_j[k] = errorSum * weight * tfunc.computeDerivative(outputs.get(j));
        }
    }

    /**
     * Update weights of incoming links.
     */
    public void updateWeight(final PropParameter param) {
        Synapse s = null;
        for (int k = 0; k < inLinks.size(); k++) {
            s = inLinks.get(k);
            double sum_j = 0.0;
            for (int l = 0; l < deltas_j.length; l++) {
                sum_j += deltas_j[l] * s.getSource().getOutput(param.pairMap[param.current][l]);
            }
            final double dw = learningRate * (delta_i * s.getSource().getOutput(param.current) - sum_j);
            s.setWeightAdjustment(dw);
            s.updateWeight();
        }
    }
}
