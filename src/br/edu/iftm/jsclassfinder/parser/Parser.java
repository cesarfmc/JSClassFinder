package br.edu.iftm.jsclassfinder.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.script.ScriptException;

import au.com.bytecode.opencsv.CSVWriter;
import br.edu.iftm.jsclassfinder.entity.Classe;
import br.edu.iftm.jsclassfinder.entity.InheritanceExpression;
import br.edu.iftm.jsclassfinder.entity.NewExpression;
import br.edu.iftm.jsclassfinder.entity.PrototypeExpression;
import rinoceronte.Esprima;

public class Parser {

	private String dirPath;
	private List<Classe> classes = new ArrayList<Classe>();
	private List<NewExpression> newExpressions = new ArrayList<NewExpression>();
	private List<PrototypeExpression> prototypeExpressions = new ArrayList<PrototypeExpression>();
	private List<InheritanceExpression> inheritanceExpressions = new ArrayList<InheritanceExpression>();
	private static int nClasses = 0;
	private static int nFunctions = 0;

	public Parser(String dirPath) {
		this.dirPath = dirPath;
	}

	/**
	 * Este n�o � o m�todo construtor. 
	 * @throws NoSuchMethodException
	 * @throws ScriptException
	 * @throws IOException
	 */
	public void parse() throws NoSuchMethodException, ScriptException, IOException {
		//System.out.format("%15s%7s%7s%7s%7s%7s%7s\n", "System", "#Files", "#Funct", "#Class", "#Meth", "#Attr",
		//		"#Inher");
		File diretorioRaiz = new File(dirPath);
		ArrayList<File> diretorios = new ArrayList<File>(); 
		diretorios.add(diretorioRaiz);
		for (File dir : diretorios) {
			if (dir.isDirectory()) {
				nFunctions = 0;
				List<File> files = listf(dir.getAbsolutePath());
				List<File> filesJSON = generateJSON(files);
				generateClasses(filesJSON);
				newExpressions = new ArrayList<NewExpression>();
				prototypeExpressions = new ArrayList<PrototypeExpression>();
				inheritanceExpressions = new ArrayList<InheritanceExpression>();
			}
		}
		printCSV(classes);
	
		// gerarXMI(classes);
	}

	private void generateClasses(List<File> filesJSON) throws FileNotFoundException {

		for (File file : filesJSON) {
			JsonReader jsonReader = Json.createReader(new FileReader(file));
			JsonObject jsonObject = jsonReader.readObject();
			JsonValue jsValue = (JsonValue) jsonObject;
			generateNewExpressionsRec(jsValue, null, null);
			String fileName = file.getAbsolutePath();
			fileName = fileName.replaceAll(dirPath, "");
			fileName = fileName.replaceAll(".json", ".js");
			generateClassesRec(jsValue, null, null, fileName);
		}
	}

	private List<File> generateJSON(List<File> files) throws NoSuchMethodException, ScriptException, IOException {
		ArrayList<File> filesJSON = new ArrayList<File>();
		for (File file : files) {
			if (file.isFile() && file.getPath().endsWith(".js")) {
				File arquivoJS = file;
				File arquivoJSON = new File(file.getPath().substring(0, file.getPath().length() - 3) + ".json");
				if (!arquivoJSON.exists()) {
					Esprima esprima = new Esprima(arquivoJS, arquivoJSON);
					esprima.parse();
				}
				filesJSON.add(arquivoJSON);
			}
		}
		return filesJSON;
	}

	private List<File> listf(String directoryName) {
		File directory = new File(directoryName);
		List<File> resultList = new ArrayList<File>();
		File[] fList = directory.listFiles();
		resultList.addAll(Arrays.asList(fList));
		for (File file : fList) {
			if (file.isFile()) {
			} else if (file.isDirectory()) {
				resultList.addAll(listf(file.getAbsolutePath()));
			}
		}
		return resultList;
	}

	private void generateNewExpressionsRec(JsonValue tree, String key, JsonObject obj) {
		if (key != null) {
			if (key.equals("type")) {
				if (tree.getValueType() == JsonValue.ValueType.STRING) {
					JsonString jSonString = (JsonString) tree;
					if (jSonString.getString().equals("NewExpression")) {
						JsonObject callee = obj.getJsonObject("callee");
						if (callee.containsKey("name")) {
							newExpressions.add(new NewExpression(callee.getString("name")));
						}
					}
					if (jSonString.getString().equals("CallExpression")) {
						JsonObject callee = obj.getJsonObject("callee");
						if (callee.containsKey("object") && callee.containsKey("property")) {
							JsonObject object = callee.getJsonObject("object");
							JsonObject property = callee.getJsonObject("property");
							if (object.containsKey("name") && property.containsKey("name")) {
								if (object.getString("name").equals("Object")
										&& property.getString("name").equals("create")) {
									JsonArray arguments = obj.getJsonArray("arguments");
									if (!arguments.isEmpty()) {
										if (arguments.getJsonObject(0).containsKey("object")
												&& arguments.getJsonObject(0).containsKey("property")) {
											JsonObject objectArg = arguments.getJsonObject(0).getJsonObject("object");
											JsonObject propertyArg = arguments.getJsonObject(0)
													.getJsonObject("property");
											if (objectArg.containsKey("name") && propertyArg.containsKey("name")) {
												if (propertyArg.getString("name").equals("prototype")) {
													newExpressions.add(new NewExpression(objectArg.getString("name")));
												}
											}
										}
									}
								}
							}
						}
					}
					if (jSonString.getString().equals("AssignmentExpression")) {
						JsonObject left = obj.getJsonObject("left");
						JsonObject right = obj.getJsonObject("right");
						if (left.getString("type").equals("MemberExpression")) {
							if (left.containsKey("object") && left.containsKey("property")) {
								JsonObject object = left.getJsonObject("object");
								JsonObject property = left.getJsonObject("property");
								if (object.containsKey("object") && object.containsKey("property")) {
									JsonObject objectIn = object.getJsonObject("object");
									JsonObject propertyIn = object.getJsonObject("property");
									if (propertyIn.containsKey("name")) {
										if (propertyIn.getString("name").equals("prototype")
												&& objectIn.containsKey("name") && property.containsKey("name")) {
											String classe = objectIn.getString("name");
											String membro = property.getString("name");
											String tipo = right.getString("type");
											PrototypeExpression ep = new PrototypeExpression(classe, membro, tipo);
											if (!prototypeExpressions.contains(ep)) {
												prototypeExpressions.add(ep);
											}

										}
									}
								} else if (object.containsKey("name") && property.containsKey("name")) {
									if (property.getString("name").equals("prototype")
											&& right.getString("type").equals("NewExpression")) {
										String classeFilha = object.getString("name");
										JsonObject callee = right.getJsonObject("callee");
										if (callee.containsKey("name")) {
											inheritanceExpressions.add(
													new InheritanceExpression(callee.getString("name"), classeFilha));
										}
									} else if (property.getString("name").equals("prototype")
											&& right.getString("type").equals("CallExpression")) {
										String classeFilha = object.getString("name");
										JsonObject callee = right.getJsonObject("callee");
										if (callee.containsKey("object") && callee.containsKey("property")) {
											JsonObject object1 = callee.getJsonObject("object");
											JsonObject property1 = callee.getJsonObject("property");
											if (object1.containsKey("name") && property1.containsKey("name")) {
												if (object1.getString("name").equals("Object")
														&& property1.getString("name").equals("create")) {
													JsonArray arguments = right.getJsonArray("arguments");
													if (!arguments.isEmpty()) {
														if (arguments.getJsonObject(0).containsKey("object")
																&& arguments.getJsonObject(0).containsKey("property")) {
															JsonObject objectArg = arguments.getJsonObject(0)
																	.getJsonObject("object");
															JsonObject propertyArg = arguments.getJsonObject(0)
																	.getJsonObject("property");
															if (objectArg.containsKey("name")
																	&& propertyArg.containsKey("name")) {
																if (propertyArg.getString("name").equals("prototype")) {
																	inheritanceExpressions
																			.add(new InheritanceExpression(
																					objectArg.getString("name"),
																					classeFilha));
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		switch (tree.getValueType()) {
		case OBJECT:
			JsonObject object = (JsonObject) tree;
			for (String name : object.keySet()) {
				generateNewExpressionsRec(object.get(name), name, object);
			}
			break;
		case ARRAY:
			JsonArray array = (JsonArray) tree;
			for (JsonValue val : array) {
				generateNewExpressionsRec(val, null, null);
			}
			break;
		}
	}

	private void generateClassesRec(JsonValue tree, String key, JsonObject obj, String path) {
		if (key != null) {
			if (key.equals("type")) {
				JsonString jSonString = (JsonString) tree;
				if (jSonString.getString().equals("FunctionDeclaration")
						|| jSonString.getString().equals("FunctionExpression")) {
					nFunctions++;
				}

				if (jSonString.getString().equals("FunctionDeclaration")) {
					// nFunctions++;
					JsonObject jsonObjecCla = obj.getJsonObject("id");
					if (isClass(jsonObjecCla.getString("name"))) {
						Classe classe = new Classe(jsonObjecCla.getString("name"), nClasses, path);
						classes.add(classe);
						JsonObject jsonObjectBody = obj.getJsonObject("body");
						generateAttributeMethods(classe, jsonObjectBody);
						generatePrototypeAttributesMethods(classe);
					}

				} else if (jSonString.getString().equals("VariableDeclaration")) {
					JsonArray jsonArrayDeclaration = obj.getJsonArray("declarations");
					for (int j = 0; j < jsonArrayDeclaration.size(); j++) {
						JsonObject jsonObject = jsonArrayDeclaration.getJsonObject(j);
						if (jsonObject.getString("type").equals("VariableDeclarator")) {
							if (jsonObject.containsKey("init") && !jsonObject.isNull("init")) {
								JsonObject jsonObjectInit = jsonObject.getJsonObject("init");
								if (jsonObjectInit.getString("type").equals("FunctionExpression")) {
									// nFunctions++;
									JsonObject jsonObjectId = jsonObject.getJsonObject("id");
									if (isClass(jsonObjectId.getString("name"))) {
										Classe classe = new Classe(jsonObjectId.getString("name"), nClasses, path);
										classes.add(classe);
										JsonObject jsonObjectBody = jsonObjectInit.getJsonObject("body");
										generateAttributeMethods(classe, jsonObjectBody);
										generatePrototypeAttributesMethods(classe);
									}
								}
							}
						}
					}
				}
			}
		}
		switch (tree.getValueType()) {
		case OBJECT:
			JsonObject object = (JsonObject) tree;
			for (String name : object.keySet()) {
				generateClassesRec(object.get(name), name, object, path);
			}
			break;
		case ARRAY:
			JsonArray array = (JsonArray) tree;
			for (JsonValue val : array) {
				generateClassesRec(val, null, null, path);
			}
			break;
		}
	}

	private void generateAttributeMethods(Classe classe, JsonObject jsonObjectBody) {
		JsonArray jsonArrayBody = jsonObjectBody.getJsonArray("body");
		for (int j = 0; j < jsonArrayBody.size(); j++) {
			JsonObject jsonObjectExp = jsonArrayBody.getJsonObject(j);
			if (jsonObjectExp.getString("type").equals("ExpressionStatement")) {
				JsonObject jsonObjectAss = jsonObjectExp.getJsonObject("expression");
				if (jsonObjectAss.getString("type").equals("AssignmentExpression")) {
					JsonObject left = jsonObjectAss.getJsonObject("left");
					if (left.containsKey("object") && left.containsKey("property")) {
						JsonObject object = left.getJsonObject("object");
						JsonObject property = left.getJsonObject("property");
						if (object.getString("type").equals("ThisExpression")) {
							String membro = property.getString("name");
							JsonObject right = jsonObjectAss.getJsonObject("right");
							if (right.getString("type").equals("FunctionExpression")) {
								classe.addMethod(membro);
							} else {
								classe.addAttribute(membro);
							}
						}
					}
				}
			}
		}
	}

	private void generatePrototypeAttributesMethods(Classe classe) {
		for (PrototypeExpression ep : prototypeExpressions) {
			if (ep.getClasse().equals(classe.getName())) {
				if (ep.getTipo().equals("FunctionExpression")) {
					classe.addMethod(ep.getMembro());
				} else {
					classe.addAttribute(ep.getMembro());
				}
			}
		}
	}

	private boolean isClass(String name) {
		return newExpressions.contains(new NewExpression(name));
	}

	private void printData(String name, int nfiles, int nfunctions, List<Classe> classes, int nInheritance) {
		
		int nClasses = 0;
		int nAttributes = 0;
		int nMethods = 0;
		for (Classe classe : classes) {
			nClasses++;
			nAttributes = nAttributes + classe.getAttributes().size();
			nMethods = nMethods + classe.getMethods().size();
		}
		System.out.format("%15s%7d%7d%7d%7d%7d%7d\n", name, nfiles, nfunctions, nClasses, nMethods, nAttributes,
				nInheritance);
	}
	
	private void printCSV(List<Classe> classes) throws IOException {
		String[] args = dirPath.split("/");
		String fileName = args[args.length-1]+".csv";
		List<String[]> data = new ArrayList<String[]>();
		for (Classe classe : classes) {
			System.out.format("%s;%s;%d;%d\n", classe.getPath(), classe.getName(), classe.getMethods().size(), classe.getAttributes().size());
			String[] entries = {classe.getPath(), classe.getName(), String.valueOf(classe.getMethods().size()), String.valueOf(classe.getAttributes().size())};
			data.add(entries);
		}
		CSVWriter writer = new CSVWriter(new FileWriter(fileName), ';');
		System.out.println(data);
		writer.writeAll(data);
		writer.close();
	}

	/*
	 * public static void gerarXMI(List<Classe> classes) { System.out.print(
	 * "Digite o nome do Modelo: "); Scanner sc = new Scanner(System.in); String
	 * nomeModelo = sc.nextLine(); int cont = 0; int idHeranca = 0;
	 * System.out.println("<XMI xmi.version = '1.2'>"); System.out.println(
	 * "    <XMI.content>"); System.out.println("        <UML:Model name = '" +
	 * nomeModelo + "'>"); System.out.println(
	 * "            <UML:Namespace.ownedElement>"); for (Classe c : classes) {
	 * System.out.println("                <UML:Class name = '" + c.getNome() +
	 * "' xmi.id = '" + c.getId() + "'>"); if (c.getSuperClasse() != null) {
	 * System.out.println(
	 * "                    <UML:GeneralizableElement.generalization>");
	 * System.out.println(
	 * "                        <UML:Generalization xmi.idref = '" + contador +
	 * "'/>"); System.out.println(
	 * "                    </UML:GeneralizableElement.generalization>");
	 * contador++; } System.out.println(
	 * "                       <UML:Classifier.feature>"); for (String a :
	 * c.getAtributos()) { System.out.println(
	 * "                            <UML:Attribute name = '" + a + "'>");
	 * System.out.println("                            </UML:Attribute>"); } for
	 * (String m : c.getMetodos()) { System.out.println(
	 * "                            <UML:Operation name = '" + m + "'>");
	 * System.out.println("                            </UML:Operation>"); }
	 * System.out.println("                        </UML:Classifier.feature>");
	 * System.out.println("                    </UML:Class>"); } for (Classe c :
	 * classes) { if (c.getSuperClasse() != null) {
	 * idHeranca=classes.size()+cont; System.out.println(
	 * "                <UML:Generalization xmi.id = '" +idHeranca +
	 * "' name = 'Heran�a'>"); System.out.println(
	 * "                    <UML:Generalization.child>"); System.out.println(
	 * "                        <UML:Class xmi.idref = '" + c.getId() + "'/>");
	 * System.out.println("                    </UML:Generalization.child>");
	 * System.out.println("                    <UML:Generalization.parent>");
	 * System.out.println("                        <UML:Class xmi.idref = '" +
	 * c.getSuperClasse().getId() + "'/>"); System.out.println(
	 * "                    </UML:Generalization.parent>"); System.out.println(
	 * "                </UML:Generalization>"); cont++; } }
	 * 
	 * System.out.println("            </UML:Namespace.ownedElement>");
	 * System.out.println("       </UML:Model>"); System.out.println(
	 * "    </XMI.content>"); System.out.println("</XMI>"); }
	 */
}
