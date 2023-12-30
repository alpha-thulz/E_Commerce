const domainName = "http://localhost:5000";


async function deleteProduct() {
    const urlParams = new URLSearchParams(window.location.search);

}
function editProduct(val) {
    const urlParams = new URLSearchParams(window.location.search);



    window.location.reload();
}

async function requestUpdate(val) {
    console.log(">>>" + val)
}

async function viewProduct() {
    const urlParams = new URLSearchParams(window.location.search);

    await fetch(domainName + "/product/" + urlParams.get("id"), {
        method: 'GET',
        headers: {
            'Accept': "application/json",
        }
    })
        .then(response => response.json())
        .then(data => {
            document.getElementById("item-name").innerText = data.name;
            document.getElementById("item-description").innerText = data.description;
            document.getElementById("item-price").innerText = `R ${data.price}`;
        });
}

async function openStore() {
    await fetch(domainName + "/products", {
        method: 'POST',
        headers: {
            'Accept': "application/json",
        }
    })
        .then(response => response.json())
        .then(data => displayResults(data));
}

function displayResults(data) {
    const resultsContainer = document.getElementById('results');

    let table = document.createElement("table");

    const headings = document.createElement('tr');
    const headingItem = document.createElement('th');
    headingItem.append("Item");
    const headingDesc = document.createElement('th');
    headingDesc.append("Description");
    const headingPrice = document.createElement('th');
    headingPrice.append("Price");
    const headingSelect = document.createElement('th');
    headingSelect.append("Delete");

    headings.append(headingItem);
    headings.append(headingDesc);
    headings.append(headingPrice);
    headings.append(headingSelect);
    table.append(headings);

    data.forEach(item => {
        const product = JSON.parse(item);

        const productRow = document.createElement('tr');
        const productID = document.createElement('a');
        const productItem = document.createElement('td');
        const productDesc = document.createElement('td');
        const productPrice = document.createElement('td');
        const productDelete = document.createElement('td');
        const checkbox = document.createElement("input");
        checkbox.type="checkbox";
        checkbox.id=`${product.id}`;
        checkbox.name=`${product.id}`;

        productID.setAttribute('href', `./product.html?id=${product.id}`);
        productID.innerText = `${product.name}`;
        productItem.appendChild(productID);
        productDesc.append(`${product.description}`);
        productPrice.append(`R${product.price}`);
        productDelete.append(checkbox);

        productRow.append(productItem);
        productRow.append(productDesc);
        productRow.append(productPrice);
        productRow.append(productDelete);
        table.append(productRow);
    });

    resultsContainer.append(table)
}

// window.onload = ()=> openStore()