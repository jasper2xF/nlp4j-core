/**
 * Copyright 2015, Emory University
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.emory.mathcs.nlp.component.template.config;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.emory.mathcs.nlp.common.util.Language;
import edu.emory.mathcs.nlp.common.util.Splitter;
import edu.emory.mathcs.nlp.common.util.XMLUtils;
import edu.emory.mathcs.nlp.component.template.train.HyperParameter;
import edu.emory.mathcs.nlp.component.template.train.LOLS;
import edu.emory.mathcs.nlp.component.template.util.TSVReader;
import edu.emory.mathcs.nlp.learning.activation.ActivationFunction;
import edu.emory.mathcs.nlp.learning.activation.SigmoidFunction;
import edu.emory.mathcs.nlp.learning.activation.SoftmaxFunction;
import edu.emory.mathcs.nlp.learning.initialization.RandomWeightGenerator;
import edu.emory.mathcs.nlp.learning.initialization.WeightGenerator;
import edu.emory.mathcs.nlp.learning.neural.FeedForwardNeuralNetworkSoftmax;
import edu.emory.mathcs.nlp.learning.optimization.OnlineOptimizer;
import edu.emory.mathcs.nlp.learning.optimization.method.AdaDeltaMiniBatch;
import edu.emory.mathcs.nlp.learning.optimization.method.AdaGrad;
import edu.emory.mathcs.nlp.learning.optimization.method.AdaGradMiniBatch;
import edu.emory.mathcs.nlp.learning.optimization.method.AdaGradRegression;
import edu.emory.mathcs.nlp.learning.optimization.method.Perceptron;
import edu.emory.mathcs.nlp.learning.optimization.method.SoftmaxRegression;
import edu.emory.mathcs.nlp.learning.optimization.reguralization.RegularizedDualAveraging;
import edu.emory.mathcs.nlp.learning.util.WeightVector;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * @author Jinho D. Choi ({@code jinho.choi@emory.edu})
 */
public class NLPConfig implements ConfigXML, Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 4121262203434095864L;
	
	protected Element xml;
	
//	=================================== CONSTRUCTORS ===================================
	
	public NLPConfig() {}
	
	public NLPConfig(InputStream in)
	{
		xml = XMLUtils.getDocumentElement(in);
	}
	
//	=================================== GETTERS & SETTERS ===================================  
	
	public Element getDocumentElement()
	{
		return xml;
	}
	
	public int getIntegerTextContent(String tagName)
	{
		return XMLUtils.getIntegerTextContentFromFirstElementByTagName(xml, tagName);
	}
	
	public String getTextContent(String tagName)
	{
		return XMLUtils.getTextContentFromFirstElementByTagName(xml, tagName);
	}
	
	public Language getLanguage()
	{
		String language = XMLUtils.getTextContentFromFirstElementByTagName(xml, LANGUAGE);
		return language == null ? Language.ENGLISH : Language.getType(language);
	}
	
	public TSVReader getTSVReader()
	{
		Element eReader = XMLUtils.getFirstElementByTagName(xml, TSV);
		Object2IntMap<String> map = getFieldMap(eReader);
		TSVReader reader = new TSVReader();
		
		reader.form   = map.getOrDefault(FIELD_FORM  , -1);
		reader.lemma  = map.getOrDefault(FIELD_LEMMA , -1);
		reader.pos    = map.getOrDefault(FIELD_POS   , -1);
		reader.nament = map.getOrDefault(FIELD_NAMENT, -1);
		reader.feats  = map.getOrDefault(FIELD_FEATS , -1);
		reader.dhead  = map.getOrDefault(FIELD_DHEAD , -1);
		reader.deprel = map.getOrDefault(FIELD_DEPREL, -1);
		reader.sheads = map.getOrDefault(FIELD_SHEADS, -1);
		
		return reader;
	}
	
	/** Called by {@link #getTSVReader()}. */
	protected Object2IntMap<String> getFieldMap(Element eTSV)
	{
		NodeList list = eTSV.getElementsByTagName(COLUMN);
		int i, index, size = list.getLength();
		Element element;
		String field;
		
		Object2IntMap<String> map = new Object2IntOpenHashMap<>();
		
		for (i=0; i<size; i++)
		{
			element = (Element)list.item(i);
			field   = XMLUtils.getTrimmedAttribute(element, FIELD);
			index   = XMLUtils.getIntegerAttribute(element, INDEX);
			
			map.put(field, index);
		}
		
		return map;
	}
	
//	=================================== FEATURE ===================================
	
	public Element getFeatureTemplateElement()
	{
		return XMLUtils.getFirstElementByTagName(xml, FEATURE_TEMPLATE);
	}
	
//	=================================== OPTIMIZER ===================================
	
	public HyperParameter getHyperParameter()
	{
		Element eOptimizer = XMLUtils.getFirstElementByTagName(xml, OPTIMIZER);
		Element eLOLS = XMLUtils.getFirstElementByTagName(eOptimizer, LOLS);
		
		int     feautureCutoff = XMLUtils.getIntegerTextContentFromFirstElementByTagName(eOptimizer, FEATURE_CUTOFF);
		int     batchSize      = XMLUtils.getIntegerTextContentFromFirstElementByTagName(eOptimizer, BATCH_SIZE);
		int     maxEpoch       = XMLUtils.getIntegerTextContentFromFirstElementByTagName(eOptimizer, MAX_EPOCH);
		float   learningRate   = XMLUtils.getFloatTextContentFromFirstElementByTagName  (eOptimizer, LEARNING_RATE);
		float   decayingRate   = XMLUtils.getFloatTextContentFromFirstElementByTagName  (eOptimizer, DECAYING_RATE);
		float   bias           = XMLUtils.getFloatTextContentFromFirstElementByTagName  (eOptimizer, BIAS);
		float   l1             = XMLUtils.getFloatTextContentFromFirstElementByTagName  (eOptimizer, L1_REGULARIZATION);

		// locally optimal learning to search
		double decaying;
		int fixed;
		
		if (eLOLS != null)
		{
			fixed    = XMLUtils.getIntegerAttribute(eLOLS, FIXED);
			decaying = XMLUtils.getDoubleAttribute(eLOLS, DECAYING);

		}
		else
		{
			fixed = 0;
			decaying = 1;
		}
		
		// l1 regularization
		RegularizedDualAveraging rda = (l1 > 0) ? new RegularizedDualAveraging(l1) : null;
		HyperParameter hp = new HyperParameter();
		
		hp.setFeature_cutoff(feautureCutoff);
		hp.setBatchSize(batchSize);
		hp.setMaxEpochs(maxEpoch);
		hp.setLearningRate(learningRate);
		hp.setDecayingRate(decayingRate);
		hp.setBias(bias);
		hp.setL1Regularizer(rda);
		hp.setLOLS(new LOLS(fixed, decaying));
		
		// neural network
		hp.setHiddenDimensions(getHiddenDimensions(eOptimizer));
		hp.setActivationFunctions(getActivationFunction(eOptimizer));
		hp.setWeightGenerator(getWeightGenerator(eOptimizer));
		
		return hp;
	}
	
	public OnlineOptimizer getOnlineOptimizer(HyperParameter hp)
	{
		Element eOptimizer = XMLUtils.getFirstElementByTagName(xml, OPTIMIZER);
		String  algorithm  = XMLUtils.getTextContentFromFirstElementByTagName(eOptimizer, ALGORITHM);
		WeightVector w = new WeightVector();
		
		switch (algorithm)
		{
		case PERCEPTRON         : return new Perceptron(w, hp.getLearningRate(), hp.getBias());
		case SOFTMAX_REGRESSION : return new SoftmaxRegression(w, hp.getLearningRate(), hp.getBias());
		case ADAGRAD_REGRESSION : return new AdaGradRegression(w, hp.getLearningRate(), hp.getBias());
		case ADAGRAD            : return new AdaGrad(w, hp.getLearningRate(), hp.getBias(), hp.getL1Regularizer());
		case ADAGRAD_MINI_BATCH : return new AdaGradMiniBatch(w, hp.getLearningRate(), hp.getBias(), hp.getL1Regularizer());
		case ADADELTA_MINI_BATCH: return new AdaDeltaMiniBatch(w, hp.getLearningRate(), hp.getDecayingRate(), hp.getBias(), hp.getL1Regularizer());
		case FFNN_SOFTMAX       : new FeedForwardNeuralNetworkSoftmax(hp.getHiddenDimensions(), hp.getActivationFunctions(), hp.getLearningRate(), hp.getBias(), hp.getWeightGenerator());
		default: throw new IllegalArgumentException(algorithm+" is not a valid algorithm name."); 
		}
	}
	
	private int[] getHiddenDimensions(Element eOptimizer)
	{
		String hidden = XMLUtils.getTextContentFromFirstElementByTagName(eOptimizer, HIDDEN_DIMENSIONS);
		if (hidden == null || hidden.isEmpty()) return null;
		String[] t = Splitter.splitCommas(hidden);
		return Arrays.stream(t).mapToInt(Integer::parseInt).toArray();
	}
	
	private ActivationFunction[] getActivationFunction(Element eOptimizer)
	{
		String activation = XMLUtils.getTextContentFromFirstElementByTagName(eOptimizer, ACTIVATION_FUNCTIONS);
		if (activation == null || activation.isEmpty()) return null;
		String[] t = Splitter.splitCommas(activation);
		ActivationFunction[] functions = new ActivationFunction[t.length];
		
		for (int i=0; i<t.length; i++)
		{
			switch (t[i])
			{
			case SIGMOID: functions[i] = new SigmoidFunction(); break;
			case SOFTMAX: functions[i] = new SoftmaxFunction(); break;
			}
		}
		
		return functions;
	}
	
	private WeightGenerator getWeightGenerator(Element eOptimizer)
	{
		Element element = XMLUtils.getFirstElementByTagName(eOptimizer, WEIGHT_GENERATOR);
		if (element == null) return null;
		
		float lower = Float.parseFloat(XMLUtils.getTrimmedAttribute(element, "lower"));
		float upper = Float.parseFloat(XMLUtils.getTrimmedAttribute(element, "upper"));
		return new RandomWeightGenerator(lower, upper); 
	}
}
