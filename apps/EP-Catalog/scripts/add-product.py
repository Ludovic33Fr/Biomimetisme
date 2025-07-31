#!/usr/bin/env python3
"""
Script pour ajouter facilement de nouveaux produits au fichier JSON
"""

import json
import os
import sys
from datetime import datetime

# Chemin vers le fichier JSON
JSON_FILE = "../src/main/resources/data/products.json"

def load_products():
    """Charge les produits existants depuis le fichier JSON"""
    try:
        with open(JSON_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"Fichier {JSON_FILE} non trouvé!")
        return []

def save_products(products):
    """Sauvegarde les produits dans le fichier JSON"""
    with open(JSON_FILE, 'w', encoding='utf-8') as f:
        json.dump(products, f, indent=2, ensure_ascii=False)

def get_next_id(products):
    """Génère le prochain ID disponible"""
    if not products:
        return "1"
    max_id = max(int(p["id"]) for p in products)
    return str(max_id + 1)

def add_product():
    """Ajoute un nouveau produit interactivement"""
    products = load_products()
    
    print("=== Ajout d'un nouveau produit ===\n")
    
    # Génération automatique de l'ID
    new_id = get_next_id(products)
    print(f"ID généré automatiquement: {new_id}")
    
    # Saisie des informations du produit
    name = input("Nom du produit: ").strip()
    description = input("Description: ").strip()
    
    while True:
        try:
            price = float(input("Prix: ").strip())
            break
        except ValueError:
            print("Prix invalide. Veuillez entrer un nombre.")
    
    category = input("Catégorie: ").strip()
    brand = input("Marque: ").strip()
    color = input("Couleur: ").strip()
    size = input("Taille: ").strip()
    
    # Création du nouveau produit
    new_product = {
        "id": new_id,
        "name": name,
        "description": description,
        "price": price,
        "category": category,
        "brand": brand,
        "color": color,
        "size": size
    }
    
    # Ajout à la liste
    products.append(new_product)
    
    # Sauvegarde
    save_products(products)
    
    print(f"\n✅ Produit ajouté avec succès! (ID: {new_id})")
    print(f"Total des produits: {len(products)}")

def list_products():
    """Affiche la liste des produits existants"""
    products = load_products()
    
    if not products:
        print("Aucun produit trouvé.")
        return
    
    print(f"\n=== Liste des {len(products)} produits ===\n")
    
    for product in products:
        print(f"ID: {product['id']}")
        print(f"Nom: {product['name']}")
        print(f"Prix: {product['price']}€")
        print(f"Catégorie: {product['category']}")
        print(f"Marque: {product['brand']}")
        print("-" * 40)

def main():
    if len(sys.argv) > 1:
        command = sys.argv[1].lower()
        
        if command == "add":
            add_product()
        elif command == "list":
            list_products()
        else:
            print("Commandes disponibles: add, list")
    else:
        print("Script de gestion des produits")
        print("Usage:")
        print("  python add-product.py add    - Ajouter un produit")
        print("  python add-product.py list   - Lister les produits")

if __name__ == "__main__":
    main() 